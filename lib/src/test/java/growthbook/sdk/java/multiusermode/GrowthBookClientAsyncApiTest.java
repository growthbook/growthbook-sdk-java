package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CompletableFuture evaluation surface: parity with the sync API, the
 * executor hop off store-completion threads, caller-cancel isolation, failure
 * propagation, and hang-proof initializeAsync/shutdown interplay. Latch-gated,
 * no sleeps.
 */
class GrowthBookClientAsyncApiTest {

    private static final String CLIENT_KEY = "async_api_key";

    private static Options stickyOptions(AsyncStickyBucketService service, TrackingCallbackWithUser tracking) {
        return Options.builder()
                .asyncStickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .trackingCallBackWithUser(tracking)
                .build();
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder()
                .key(key)
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .meta(new ArrayList<>(Arrays.asList(
                        VariationMeta.builder().key("control").build(),
                        VariationMeta.builder().key("treatment").build())))
                .build();
    }

    private static UserContext user(String id) {
        return UserContext.builder().attributesJson("{\"id\":\"" + id + "\"}").build();
    }

    /** The variation key the user is NOT hashed into, so a stored assignment differs from normal bucketing. */
    private static String variationKeyOtherThanHashed(Experiment<String> experiment, UserContext user) {
        GrowthBookClient plain = new GrowthBookClient(Options.builder().build());
        try {
            return plain.run(experiment, user).getVariationId() == 0 ? "treatment" : "control";
        } finally {
            plain.shutdown();
        }
    }

    private static StickyAssignmentsDocument storedAssignment(String userId, String experimentKey, String variationKey) {
        Map<String, String> assignments = new HashMap<>();
        assignments.put(experimentKey + "__0", variationKey);
        return new StickyAssignmentsDocument("id", userId, assignments);
    }

    @Test
    @Timeout(30)
    void asyncEvaluationMatchesSyncResults() throws Exception {
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            ExperimentResult<String> sync = client.run(experiment("exp-parity"), user("u1"));
            ExperimentResult<String> async = client.runAsync(experiment("exp-parity"), user("u1"))
                    .get(10, TimeUnit.SECONDS);

            assertEquals(sync.getInExperiment(), async.getInExperiment());
            assertEquals(sync.getVariationId(), async.getVariationId());
            assertEquals(sync.getValue(), async.getValue());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void withoutStickyServiceAsyncFuturesAreAlreadyComplete() {
        GrowthBookClient client = new GrowthBookClient();
        try {
            CompletableFuture<Boolean> isOn = client.isOnAsync("nope", user("u1"));
            assertTrue(isOn.isDone(), "no sticky service and no remote eval → nothing to wait for");
            assertFalse(isOn.join());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void evaluationAndTrackingNeverRunOnTheStoreCompletionThread() throws Exception {
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        List<String> trackingThreads = new CopyOnWriteArrayList<>();
        TrackingCallbackWithUser tracking = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                trackingThreads.add(Thread.currentThread().getName());
            }
        };
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, tracking));
        try {
            client.runAsync(experiment("exp-hop"), user("u1")).get(10, TimeUnit.SECONDS);

            assertFalse(trackingThreads.isEmpty(), "tracking callback did not fire");
            for (String threadName : trackingThreads) {
                assertFalse(threadName.equals("store-io-thread"),
                        "evaluation (and the user's tracking callback) ran on the store's completion thread");
                assertTrue(threadName.startsWith("growthbook-async-"),
                        "expected the SDK executor, got: " + threadName);
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void failedStickyFetchStillRunsCallerContinuationsOffTheStoreThread() throws Exception {
        // The store fails its fetch on its own completion thread; evaluation and the caller's
        // continuation must run on the SDK executor, never on the store's thread (which may be a
        // Netty event loop).
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            List<String> handlerThreads = new CopyOnWriteArrayList<>();
            CountDownLatch handled = new CountDownLatch(1);
            client.isOnAsync("any", user("u1")).whenComplete((isOn, throwable) -> {
                handlerThreads.add(Thread.currentThread().getName());
                handled.countDown();
            });

            assertTrue(handled.await(10, TimeUnit.SECONDS), "the caller's continuation never ran");
            for (String threadName : handlerThreads) {
                assertFalse(threadName.equals("store-io-thread"),
                        "a failed fetch ran the caller continuation on the store's completion thread");
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void cancellingOneCallerDoesNotAffectACoalescedOther() throws Exception {
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        CountDownLatch fetchGate = new CountDownLatch(1);
        service.fetchGate = fetchGate;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            CompletableFuture<ExperimentResult<String>> first = client.runAsync(experiment("exp-cancel"), user("u1"));
            CompletableFuture<ExperimentResult<String>> second = client.runAsync(experiment("exp-cancel"), user("u1"));

            first.cancel(true);
            fetchGate.countDown();

            assertNotNull(second.get(10, TimeUnit.SECONDS),
                    "cancelling one caller must not poison the shared coalesced fetch");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void stickyFetchFailureContinuesWithoutSaving() throws Exception {
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            ExperimentResult<String> result = client.runAsync(experiment("exp-async-fail"), user("u1"))
                    .get(10, TimeUnit.SECONDS);

            assertNotNull(result.getValue(), "a failing store must fall back to normal bucketing");
            client.flushStickyBucketSaves().get(10, TimeUnit.SECONDS);
            assertEquals(0, service.saveCalls.get(),
                    "an assignment bucketed without the stored documents must not be saved");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void failedReadOnTheAsyncApiNeverReplacesTheStoredAssignment() throws Exception {
        Experiment<String> experiment = experiment("exp-async-keep");
        String stored = variationKeyOtherThanHashed(experiment, user("u1"));
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        service.store.put("id||u1", storedAssignment("u1", "exp-async-keep", stored));
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            service.failFetches = true;
            client.runAsync(experiment, user("u1")).get(10, TimeUnit.SECONDS);
            client.flushStickyBucketSaves().get(10, TimeUnit.SECONDS);

            assertEquals(stored, service.store.get("id||u1").getAssignments().get("exp-async-keep__0"),
                    "a failed read must leave the stored variation untouched");

            service.failFetches = false;
            ExperimentResult<String> recovered = client.runAsync(experiment, user("u1")).get(10, TimeUnit.SECONDS);

            assertEquals(stored, recovered.getKey(),
                    "once the store is readable again the user must get the stored variation back");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void saturatedExecutorFailsTheAsyncEvaluationAndSavesNothing() {
        Experiment<String> experiment = experiment("exp-async-saturated");
        String stored = variationKeyOtherThanHashed(experiment, user("u1"));
        InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl();
        service.saveAssignments(storedAssignment("u1", "exp-async-saturated", stored));
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .asyncExecutor(command -> {
                    throw new RejectedExecutionException("pool saturated");
                })
                .build());
        try {
            CompletableFuture<ExperimentResult<String>> future = client.runAsync(experiment, user("u1"));

            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> future.get(10, TimeUnit.SECONDS),
                    "a blocking store that cannot be offloaded must fail the async evaluation, not re-bucket");
            assertTrue(error.getCause() instanceof RejectedExecutionException);
            assertEquals(stored, service.getAssignments("id", "u1").getAssignments().get("exp-async-saturated__0"),
                    "a rejected read must leave the stored variation untouched");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void syncServiceFetchFailureIsLoggedAndAsyncEvaluationContinues() throws Exception {
        FailingSyncStickyService service = new FailingSyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            ExperimentResult<String> result = client.runAsync(experiment("exp-sync-fail"), user("u1"))
                    .get(10, TimeUnit.SECONDS);

            assertNotNull(result.getValue(), "a failing blocking service must fall back to normal bucketing");
            client.flushStickyBucketSaves().get(10, TimeUnit.SECONDS);
            assertEquals(0, service.saves.get(),
                    "an assignment bucketed without the stored documents must not be saved");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void getFeatureValueAsyncReturnsDefaultWhenTheStickyStoreFails() throws Exception {
        // Parity with synchronous getFeatureValue: a failed sticky read falls back to the default
        // instead of surfacing the failure to the caller.
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            String value = client.getFeatureValueAsync("missing", "fallback", String.class, user("u1"))
                    .get(10, TimeUnit.SECONDS);
            assertEquals("fallback", value, "a failed sticky read must fall back to the default value");
        } finally {
            client.shutdown();
        }
    }

    // ------------------------------------------------------------ initializeAsync

    @Test
    @Timeout(30)
    void initializeAsyncLoadsFeaturesWithoutBlockingTheCaller() throws Exception {
        WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        GrowthBookClient client = null;
        try {
            wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"features\":{\"new-home\":{\"defaultValue\":true}}}")));

            client = new GrowthBookClient(Options.builder()
                    .apiHost("http://localhost:" + wireMock.port())
                    .clientKey(CLIENT_KEY)
                    .build());

            assertTrue(client.initializeAsync().get(10, TimeUnit.SECONDS));
            assertTrue(client.isOn("new-home", user("u1")));
        } finally {
            if (client != null) {
                client.shutdown();
            }
            wireMock.stop();
        }
    }

    @Test
    @Timeout(30)
    void initializeAsyncCompletesFalseOnInvalidOptions() throws Exception {
        GrowthBookClient client = new GrowthBookClient(Options.builder().build()); // no apiHost/clientKey
        try {
            assertFalse(client.initializeAsync().get(10, TimeUnit.SECONDS),
                    "initializeAsync mirrors initialize()'s no-throw contract");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void shutdownDuringInitializeAsyncNeverLeavesTheFutureHanging() throws Exception {
        // Occupy the (caller-supplied) async executor so the initializeAsync task stays queued, then
        // shut down: the discarded task's future must be completed (false), never left hanging. No
        // network — initialize() short-circuits to false on the empty options.
        ExecutorService callerPool = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "caller-async");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch releaseOccupier = new CountDownLatch(1);
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .asyncExecutor(callerPool)
                .build());
        try {
            callerPool.submit(() -> releaseOccupier.await(10, TimeUnit.SECONDS)); // holds the only thread

            CompletableFuture<Boolean> initializing = client.initializeAsync(); // queues behind the occupier
            client.shutdown();

            assertFalse(initializing.get(10, TimeUnit.SECONDS),
                    "a shutdown mid-initialize must complete the future (false), never hang it");
        } finally {
            releaseOccupier.countDown();
            client.shutdown();
            callerPool.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    void shutdownIsIdempotent() {
        GrowthBookClient client = new GrowthBookClient();
        client.shutdown();
        client.shutdown(); // second call returns immediately, no exception
    }

    /** Async service completing fetches on its own named thread. */
    private static final class ForeignThreadStickyService implements AsyncStickyBucketService {
        final Map<String, StickyAssignmentsDocument> store = new ConcurrentHashMap<>();
        final AtomicInteger saveCalls = new AtomicInteger();
        volatile CountDownLatch fetchGate = null;
        volatile boolean failFetches = false;

        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return CompletableFuture.completedFuture(store.get(getKey(attributeName, attributeValue)));
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
            saveCalls.incrementAndGet();
            store.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
            CompletableFuture<Map<String, StickyAssignmentsDocument>> pending = new CompletableFuture<>();
            CountDownLatch gate = fetchGate;
            Thread completer = new Thread(() -> {
                try {
                    if (gate != null && !gate.await(10, TimeUnit.SECONDS)) {
                        pending.completeExceptionally(new IllegalStateException("fetch gate never opened"));
                        return;
                    }
                    if (failFetches) {
                        pending.completeExceptionally(new IllegalStateException("store unavailable"));
                    } else {
                        Map<String, StickyAssignmentsDocument> found = new HashMap<>();
                        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                            StickyAssignmentsDocument doc = store.get(getKey(attribute.getKey(), attribute.getValue()));
                            if (doc != null) {
                                found.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                            }
                        }
                        pending.complete(found);
                    }
                } catch (InterruptedException e) {
                    pending.completeExceptionally(e);
                }
            }, "store-io-thread");
            completer.setDaemon(true);
            completer.start();
            return pending;
        }
    }

    /** Blocking service whose reads always fail, as during a store outage. */
    private static final class FailingSyncStickyService implements StickyBucketService {
        final AtomicInteger saves = new AtomicInteger();

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            throw new IllegalStateException("store unavailable");
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            saves.incrementAndGet();
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            throw new IllegalStateException("store unavailable");
        }
    }
}
