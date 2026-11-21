package growthbook.sdk.java.multiusermode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.OptionsValidator;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client-level behavior of async sticky bucketing: identifier-scoped reads,
 * sync-service offload off the caller thread with batched lookups,
 * fire-and-forget writes with flush, prefetch, failure propagation, and
 * executor ownership. Inline experiments are used so no feature fetch or
 * network is involved; all gating is latch-based, no sleeps.
 */
class GrowthBookClientStickyBucketTest {

    private static final long GATE_TIMEOUT_SECONDS = 10;

    /** Async in-memory service with gates and counters. */
    private static final class GatedAsyncStickyService implements AsyncStickyBucketService {
        final Map<String, StickyAssignmentsDocument> store = new ConcurrentHashMap<>();
        final List<Map<String, String>> fetchCalls = new CopyOnWriteArrayList<>();
        final AtomicInteger saveCalls = new AtomicInteger();
        volatile CountDownLatch saveGate = null; // when set, saves complete only after the gate opens
        volatile boolean failFetches = false;
        volatile boolean hangFetches = false; // when set, reads never complete

        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return CompletableFuture.completedFuture(store.get(getKey(attributeName, attributeValue)));
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
            saveCalls.incrementAndGet();
            CountDownLatch gate = saveGate;
            if (gate == null) {
                store.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> pending = new CompletableFuture<>();
            Thread completer = new Thread(() -> {
                try {
                    if (gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        store.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                        pending.complete(null);
                    } else {
                        pending.completeExceptionally(new IllegalStateException("save gate never opened"));
                    }
                } catch (InterruptedException e) {
                    pending.completeExceptionally(e);
                }
            }, "gated-save-completer");
            completer.setDaemon(true);
            completer.start();
            return pending;
        }

        @Override
        public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
            fetchCalls.add(attributes);
            if (hangFetches) {
                return new CompletableFuture<>();
            }
            if (failFetches) {
                CompletableFuture<Map<String, StickyAssignmentsDocument>> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("store unavailable"));
                return failed;
            }
            Map<String, StickyAssignmentsDocument> result = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                StickyAssignmentsDocument doc = store.get(getKey(attribute.getKey(), attribute.getValue()));
                if (doc != null) {
                    result.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                }
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    /** Sync service instrumented to record calling threads and batch counts. */
    private static final class RecordingSyncStickyService implements StickyBucketService {
        final InMemoryStickyBucketServiceImpl delegate = new InMemoryStickyBucketServiceImpl();
        final List<String> fetchThreads = new CopyOnWriteArrayList<>();
        final List<String> saveThreads = new CopyOnWriteArrayList<>();
        final AtomicInteger batchedFetchCalls = new AtomicInteger();
        volatile boolean failFetches = false;

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            return delegate.getAssignments(attributeName, attributeValue);
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            saveThreads.add(Thread.currentThread().getName());
            delegate.saveAssignments(doc);
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            batchedFetchCalls.incrementAndGet();
            fetchThreads.add(Thread.currentThread().getName());
            if (failFetches) {
                throw new IllegalStateException("store unavailable");
            }
            return delegate.getAllAssignments(attributes);
        }
    }

    private static Experiment<String> experiment(String key) {
        // meta keys are required for sticky lookups to map a persisted variation
        // key back to an index — same behavior as the JS SDK (GrowthBook-served
        // experiments always carry meta).
        return Experiment.<String>builder()
                .key(key)
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .meta(new ArrayList<>(Arrays.asList(
                        growthbook.sdk.java.model.VariationMeta.builder().key("control").build(),
                        growthbook.sdk.java.model.VariationMeta.builder().key("treatment").build())))
                .build();
    }

    private static UserContext user(String id, String extraAttr) {
        return UserContext.builder()
                .attributesJson("{\"id\":\"" + id + "\",\"" + extraAttr + "\":\"x\",\"premium\":true}")
                .build();
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

    private static Options stickyOptions(GatedAsyncStickyService service) {
        return Options.builder()
                .asyncStickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build();
    }

    // ---------------------------------------------------------------- reads

    @Test
    @Timeout(30)
    void inlineExperimentIdentifierIsFetchedWithoutConfiguredOrDerivedIdentifiers() {
        // No configured stickyBucketIdentifierAttributes and no loaded features, so the only identifier
        // available is the inline experiment's own hashAttribute. It must still be read from the store.
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .asyncStickyBucketService(service)
                .build());
        try {
            Experiment<String> experiment = Experiment.<String>builder()
                    .key("exp-inline")
                    .hashAttribute("userId")
                    .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                    .meta(new ArrayList<>(Arrays.asList(
                            growthbook.sdk.java.model.VariationMeta.builder().key("control").build(),
                            growthbook.sdk.java.model.VariationMeta.builder().key("treatment").build())))
                    .build();
            UserContext userContext = UserContext.builder().attributesJson("{\"userId\":\"u1\"}").build();

            client.run(experiment, userContext);

            assertFalse(service.fetchCalls.isEmpty(), "a sticky read must happen for the inline experiment");
            boolean fetchedInlineIdentifier = service.fetchCalls.stream().anyMatch(c -> c.containsKey("userId"));
            assertTrue(fetchedInlineIdentifier,
                    "the inline experiment's hashAttribute must be fetched; calls=" + service.fetchCalls);
        } finally {
            client.shutdown();
        }
    }

    /** A client whose loaded feature runs an experiment hashed on {@code deviceId}. */
    private static GrowthBookClient clientWithDeviceIdExperiment(WireMockServer wireMock,
                                                                 GatedAsyncStickyService service,
                                                                 List<String> configuredIdentifiers) {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"features\":{\"exp-flag\":{\"defaultValue\":\"off\",\"rules\":[{\"key\":\"exp-flag\","
                        + "\"hashAttribute\":\"deviceId\",\"variations\":[\"control\",\"treatment\"],"
                        + "\"meta\":[{\"key\":\"0\"},{\"key\":\"1\"}]}]}}}")));
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sticky-identifiers")
                .isCacheDisabled(true)
                .asyncStickyBucketService(service)
                .stickyBucketIdentifierAttributes(configuredIdentifiers)
                .build());
        assertTrue(client.initialize());
        return client;
    }

    /** Stores, for {@code deviceId||d1}, the variation the user is NOT hashed into; returns its value. */
    private static String storeTheOtherVariation(WireMockServer wireMock, GatedAsyncStickyService service) {
        GrowthBookClient plain = clientWithDeviceIdExperiment(wireMock, new GatedAsyncStickyService(), null);
        String hashed;
        try {
            hashed = String.valueOf(plain.evalFeature("exp-flag", String.class, deviceUser()).getValue());
        } finally {
            plain.shutdown();
        }
        Map<String, String> assignments = new HashMap<>();
        assignments.put("exp-flag__0", "control".equals(hashed) ? "1" : "0");
        assignments.put("other-exp__0", "1");
        service.store.put("deviceId||d1", new StickyAssignmentsDocument("deviceId", "d1", assignments));
        return "control".equals(hashed) ? "treatment" : "control";
    }

    private static UserContext deviceUser() {
        return UserContext.builder().attributesJson("{\"id\":\"u1\",\"deviceId\":\"d1\"}").build();
    }

    @Test
    @Timeout(30)
    void aStoredAssignmentOnAnAttributeOutsideTheConfiguredListIsHonoured() {
        WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        String stored = storeTheOtherVariation(wireMock, service);
        // The configured list names only "id"; the experiment hashes on "deviceId".
        GrowthBookClient client = clientWithDeviceIdExperiment(wireMock, service, Collections.singletonList("id"));
        try {
            FeatureResult<String> result = client.evalFeature("exp-flag", String.class, deviceUser());
            client.flushStickyBucketSaves().join();

            assertEquals(stored, String.valueOf(result.getValue()), "the stored variation must win over normal bucketing");
            assertEquals(0, service.saveCalls.get(), "nothing new was assigned, so nothing is saved");
            assertEquals("1", service.store.get("deviceId||d1").getAssignments().get("other-exp__0"),
                    "the rest of the stored document must be untouched");
            assertTrue(service.fetchCalls.get(service.fetchCalls.size() - 1).keySet()
                            .containsAll(Arrays.asList("id", "deviceId")),
                    "configured and derived identifiers are both read; calls=" + service.fetchCalls);
        } finally {
            client.shutdown();
            wireMock.stop();
        }
    }

    @Test
    @Timeout(30)
    void withoutAConfiguredListTheLoadedFeaturesHashAttributesAreRead() {
        WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        String stored = storeTheOtherVariation(wireMock, service);
        GrowthBookClient client = clientWithDeviceIdExperiment(wireMock, service, null);
        try {
            FeatureResult<String> result = client.evalFeature("exp-flag", String.class, deviceUser());

            assertEquals(stored, String.valueOf(result.getValue()));
            assertEquals(Collections.singleton("deviceId"),
                    service.fetchCalls.get(service.fetchCalls.size() - 1).keySet(),
                    "only the attribute the loaded experiment hashes on is read");
        } finally {
            client.shutdown();
            wireMock.stop();
        }
    }

    @Test
    @Timeout(30)
    void fetchesAreScopedToIdentifierAttributes() {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            client.run(experiment("exp-scope"), user("u1", "country"));

            assertFalse(service.fetchCalls.isEmpty());
            for (Map<String, String> call : service.fetchCalls) {
                assertEquals(Collections.singleton("id"), call.keySet(),
                        "only identifier attributes may be sent to the store");
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void synchronousEvaluationCallsABlockingServiceOnTheCallingThread() {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            client.run(experiment("exp-inline"), user("u1", "country"));

            assertEquals(1, service.batchedFetchCalls.get(),
                    "a blocking service must be read with ONE batched getAllAssignments call");
            String caller = Thread.currentThread().getName();
            assertEquals(Collections.singletonList(caller), service.fetchThreads,
                    "a synchronous evaluation must read a blocking service on its own thread");
            assertEquals(Collections.singletonList(caller), service.saveThreads,
                    "a synchronous evaluation must write a blocking service on its own thread");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void prefetchOffloadsABlockingServiceToTheExecutor() throws Exception {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            client.prefetchStickyBuckets(user("u1", "country")).get(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, service.fetchThreads.size());
            assertTrue(service.fetchThreads.get(0).startsWith("growthbook-async-"),
                    "an asynchronous entry point must not block its caller on a blocking service, ran on: "
                            + service.fetchThreads.get(0));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void callerPreloadedDocsSkipTheFetch() {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            UserContext preloaded = UserContext.builder()
                    .attributesJson("{\"id\":\"u1\"}")
                    .stickyBucketAssignmentDocs(new ConcurrentHashMap<>())
                    .build();
            client.run(experiment("exp-preload"), preloaded);

            assertEquals(0, service.fetchCalls.size(), "preloaded docs must skip the store fetch");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void prefetchOnceThenManyEvaluationsFetchOnlyOnce() throws Exception {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            UserContext requestContext = user("u1", "country");
            UserContext prefetched = client.prefetchStickyBuckets(requestContext).get(5, TimeUnit.SECONDS);
            assertNotNull(prefetched.getStickyBucketAssignmentDocs());

            for (int i = 0; i < 10; i++) {
                client.run(experiment("exp-" + i), prefetched);
            }

            assertEquals(1, service.fetchCalls.size(),
                    "prefetch-per-request means N evaluations do zero further sticky I/O");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void fetchFailureFromAsyncServiceContinuesWithoutSaving() {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            ExperimentResult<String> result = client.run(experiment("exp-fail"), user("u1", "country"));

            assertNotNull(result.getValue(), "a failing store must fall back to normal bucketing");
            client.flushStickyBucketSaves().join();
            assertEquals(0, service.saveCalls.get(),
                    "an assignment bucketed without the stored documents must not be saved");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void failedReadNeverReplacesTheStoredAssignmentInAnAsyncStore() {
        Experiment<String> experiment = experiment("exp-keep-async");
        UserContext user = user("u1", "country");
        String stored = variationKeyOtherThanHashed(experiment, user);
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        service.store.put("id||u1", storedAssignment("u1", "exp-keep-async", stored));
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            service.failFetches = true;
            client.run(experiment, user);
            client.flushStickyBucketSaves().join();

            assertEquals(stored, service.store.get("id||u1").getAssignments().get("exp-keep-async__0"),
                    "a failed read must leave the stored variation untouched");

            service.failFetches = false;
            ExperimentResult<String> recovered = client.run(experiment, user);

            assertEquals(stored, recovered.getKey(),
                    "once the store is readable again the user must get the stored variation back");
            assertTrue(Boolean.TRUE.equals(recovered.getStickyBucketUsed()));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void aStoreThatNeverAnswersDoesNotHangEvaluationOrReplaceTheStoredAssignment() throws Exception {
        Experiment<String> experiment = experiment("exp-hang");
        UserContext user = user("u1", "country");
        String stored = variationKeyOtherThanHashed(experiment, user);
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        service.store.put("id||u1", storedAssignment("u1", "exp-hang", stored));
        ManualTimeoutScheduler timer = new ManualTimeoutScheduler();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .asyncStickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .stickyBucketTimeout(Duration.ofSeconds(3))
                .build(), timer);
        try {
            service.hangFetches = true;

            // run() waits on the read, so it needs its own thread while this one lets the timeout elapse.
            CompletableFuture<ExperimentResult<String>> evaluation =
                    CompletableFuture.supplyAsync(() -> client.run(experiment, user));
            assertEquals(3_000, timer.elapseNext(), "the read is timed with Options.stickyBucketTimeout");
            ExperimentResult<String> result = evaluation.get(10, TimeUnit.SECONDS);

            assertNotNull(result, "evaluation must return once the read times out");
            assertEquals(0, service.saveCalls.get(), "a timed-out read is a failed read: nothing is saved");
            assertEquals(stored, service.store.get("id||u1").getAssignments().get("exp-hang__0"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void fetchFailureFromSyncServiceIsLoggedAndEvaluationContinues() {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            ExperimentResult<String> result = client.run(experiment("exp-sync-fail"), user("u1", "country"));

            assertNotNull(result.getValue(), "a failing blocking service must fall back to normal bucketing");
            assertEquals(1, service.batchedFetchCalls.get());
            assertTrue(service.saveThreads.isEmpty(),
                    "an assignment bucketed without the stored documents must not be saved");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void failedReadNeverReplacesTheStoredAssignmentInABlockingStore() {
        Experiment<String> experiment = experiment("exp-keep-sync");
        UserContext user = user("u1", "country");
        String stored = variationKeyOtherThanHashed(experiment, user);
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        service.delegate.saveAssignments(storedAssignment("u1", "exp-keep-sync", stored));
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            service.failFetches = true;
            client.run(experiment, user);

            assertEquals(stored, service.delegate.getAssignments("id", "u1").getAssignments().get("exp-keep-sync__0"),
                    "a failed read must leave the stored variation untouched");

            service.failFetches = false;
            ExperimentResult<String> recovered = client.run(experiment, user);

            assertEquals(stored, recovered.getKey(),
                    "once the store is readable again the user must get the stored variation back");
            assertTrue(Boolean.TRUE.equals(recovered.getStickyBucketUsed()));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void readFailureStillServesAssignmentsThisProcessMadeWhileTheStoreWasReadable() {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            ExperimentResult<String> first = client.run(experiment("exp-sync-ryw"), user("u1", "country"));
            service.failFetches = true;
            ExperimentResult<String> second = client.run(experiment("exp-sync-ryw"), user("u1", "country"));

            assertEquals(first.getVariationId(), second.getVariationId());
            assertTrue(Boolean.TRUE.equals(second.getStickyBucketUsed()),
                    "the fallback must still overlay this process's own assignment");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void synchronousEvaluationIsUnaffectedByASaturatedExecutor() {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .asyncExecutor(new RejectingExecutor())
                .build());
        try {
            ExperimentResult<String> first = client.run(experiment("exp-saturated"), user("u1", "country"));
            ExperimentResult<String> second = client.run(experiment("exp-saturated"), user("u1", "country"));

            assertEquals(2, service.batchedFetchCalls.get(), "each synchronous read must reach the service");
            assertNotNull(service.delegate.getAssignments("id", "u1"), "the assignment must reach the store");
            assertEquals(first.getVariationId(), second.getVariationId());
            assertTrue(Boolean.TRUE.equals(second.getStickyBucketUsed()),
                    "the second evaluation must be served from the stored assignment");
        } finally {
            client.shutdown();
        }
    }

    // ---------------------------------------------------------------- writes

    @Test
    @Timeout(30)
    void writesAreFireAndForgetAndFlushWaitsForThem() throws Exception {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        CountDownLatch saveGate = new CountDownLatch(1);
        service.saveGate = saveGate;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            // Evaluation returns while the save is still gated — never blocks on persistence.
            ExperimentResult<String> result = client.run(experiment("exp-write"), user("u1", "country"));
            assertNotNull(result);
            assertTrue(service.saveCalls.get() >= 1, "assignment must schedule a save");
            assertTrue(service.store.isEmpty(), "evaluation must not wait for persistence");

            CompletableFuture<Void> flush = client.flushStickyBucketSaves();
            assertFalse(flush.isDone(), "flush must wait for the gated save");

            saveGate.countDown();
            flush.get(10, TimeUnit.SECONDS);
            assertNotNull(service.store.get("id||u1"), "flushed save must be persisted");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void newAssignmentIsVisibleToLaterEvaluationsBeforePersistenceCompletes() {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        CountDownLatch saveGate = new CountDownLatch(1);
        service.saveGate = saveGate;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            UserContext firstEval = user("u1", "country");
            ExperimentResult<String> first = client.run(experiment("exp-ryw"), firstEval);

            // Same user, fresh context, save still in flight: the authoritative
            // overlay must serve the assignment (read-your-writes in-process).
            ExperimentResult<String> second = client.run(experiment("exp-ryw"), user("u1", "country"));
            assertEquals(first.getVariationId(), second.getVariationId());
            assertTrue(Boolean.TRUE.equals(second.getStickyBucketUsed()),
                    "second evaluation must be served by the sticky assignment, not re-hashed");
        } finally {
            saveGate.countDown();
            client.shutdown();
        }
    }

    // -------------------------------------------------------------- lifecycle

    @Test
    @Timeout(30)
    void shutdownFlushesPendingSaves() {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        client.run(experiment("exp-shutdown"), user("u1", "country"));
        client.shutdown();
        assertNotNull(service.store.get("id||u1"), "shutdown must flush pending saves");
    }

    @Test
    @Timeout(30)
    void anEvaluationAfterShutdownStillReadsABlockingStore() {
        // A request still in flight when the application shuts the client down must get the
        // user's stored variation, as it did before the manager existed.
        Experiment<String> experiment = experiment("exp-after-shutdown");
        UserContext user = user("u1", "country");
        String stored = variationKeyOtherThanHashed(experiment, user);
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        service.delegate.saveAssignments(storedAssignment("u1", "exp-after-shutdown", stored));
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        client.shutdown();

        ExperimentResult<String> result = client.run(experiment, user);

        assertEquals(stored, result.getKey());
        assertTrue(Boolean.TRUE.equals(result.getStickyBucketUsed()));
    }

    @Test
    @Timeout(30)
    void callerSuppliedExecutorIsNeverShutDown() throws Exception {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        java.util.concurrent.ExecutorService callerPool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "caller-pool");
            t.setDaemon(true);
            return t;
        });
        try {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .stickyBucketService(service)
                    .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                    .asyncExecutor(callerPool)
                    .build());
            client.run(experiment("exp-exec"), user("u1", "country"));
            client.shutdown();

            assertFalse(callerPool.isShutdown(), "the SDK must never shut down a caller-supplied executor");
            // Still usable after client shutdown.
            callerPool.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } finally {
            callerPool.shutdownNow();
        }
    }

    // ------------------------------------------------------------- validation

    @Test
    void configuringBothServiceFlavorsIsRejected() {
        Options options = Options.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-key")
                .stickyBucketService(new InMemoryStickyBucketServiceImpl())
                .asyncStickyBucketService(new GatedAsyncStickyService())
                .build();
        List<String> violations = OptionsValidator.findViolations(options);
        assertTrue(violations.stream().anyMatch(v -> v.contains("not both")),
                "expected mutual-exclusion violation, got: " + violations);
    }

    /** Executor at saturation: rejects every task, as a bounded pool with a full queue would. */
    private static final class RejectingExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("pool saturated");
        }
    }
}
