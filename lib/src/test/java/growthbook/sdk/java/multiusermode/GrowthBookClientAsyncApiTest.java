package growthbook.sdk.java.multiusermode;

import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

    /** Async service completing fetches on its own named thread. */
    private static final class ForeignThreadStickyService implements AsyncStickyBucketService {
        final Map<String, StickyAssignmentsDocument> store = new HashMap<>();
        volatile CountDownLatch fetchGate = null;
        volatile boolean failFetches = false;

        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return CompletableFuture.completedFuture(store.get(getKey(attributeName, attributeValue)));
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
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
                        pending.complete(new HashMap<>());
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
    void stickyFetchFailureCompletesTheFutureExceptionally() {
        ForeignThreadStickyService service = new ForeignThreadStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service, null));
        try {
            CompletableFuture<Boolean> future = client.isOnAsync("any", user("u1"));
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> future.get(10, TimeUnit.SECONDS));
            assertNotNull(error.getCause());
        } finally {
            client.shutdown();
        }
    }

    // ------------------------------------------------------------ initializeAsync

    @Test
    @Timeout(30)
    void initializeAsyncLoadsFeaturesWithoutBlockingTheCaller() throws Exception {
        HttpServer server = startFeatureServer("{\"features\":{\"new-home\":{\"defaultValue\":true}}}", null);
        GrowthBookClient client = null;
        try {
            client = new GrowthBookClient(Options.builder()
                    .apiHost("http://127.0.0.1:" + server.getAddress().getPort())
                    .clientKey(CLIENT_KEY)
                    .build());

            assertTrue(client.initializeAsync().get(10, TimeUnit.SECONDS));
            assertTrue(client.isOn("new-home", user("u1")));
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
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
        CountDownLatch responseGate = new CountDownLatch(1);
        HttpServer server = startFeatureServer("{\"features\":{}}", responseGate);
        GrowthBookClient client = null;
        try {
            client = new GrowthBookClient(Options.builder()
                    .apiHost("http://127.0.0.1:" + server.getAddress().getPort())
                    .clientKey(CLIENT_KEY)
                    .build());

            CompletableFuture<Boolean> initializing = client.initializeAsync();
            client.shutdown(); // cancels the in-flight enqueue()d call

            assertFalse(initializing.get(10, TimeUnit.SECONDS),
                    "a shutdown mid-initialize must complete the future (false), never hang it");
        } finally {
            responseGate.countDown();
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    @Test
    @Timeout(30)
    void shutdownIsIdempotent() {
        GrowthBookClient client = new GrowthBookClient();
        client.shutdown();
        client.shutdown(); // second call returns immediately, no exception
    }

    private static HttpServer startFeatureServer(String body, CountDownLatch responseGate) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/" + CLIENT_KEY, exchange -> {
            if (responseGate != null) {
                try {
                    responseGate.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(responseBytes);
            }
            exchange.close();
        });
        server.start();
        return server;
    }
}
