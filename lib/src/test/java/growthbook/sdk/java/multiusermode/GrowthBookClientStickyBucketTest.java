package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.OptionsValidator;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        final AtomicInteger batchedFetchCalls = new AtomicInteger();

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            return delegate.getAssignments(attributeName, attributeValue);
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            delegate.saveAssignments(doc);
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            batchedFetchCalls.incrementAndGet();
            fetchThreads.add(Thread.currentThread().getName());
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

    private static Options stickyOptions(GatedAsyncStickyService service) {
        return Options.builder()
                .asyncStickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build();
    }

    // ---------------------------------------------------------------- reads

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
    void syncServiceIsOffloadedAndBatched() {
        RecordingSyncStickyService service = new RecordingSyncStickyService();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            client.run(experiment("exp-offload"), user("u1", "country"));

            assertEquals(1, service.batchedFetchCalls.get(),
                    "sync service must be offloaded as ONE batched getAllAssignments call");
            for (String threadName : service.fetchThreads) {
                assertTrue(threadName.startsWith("growthbook-async-"),
                        "blocking sync service must run on the SDK pool, ran on: " + threadName);
            }
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
    void fetchFailurePropagatesOnTheSyncPath() {
        GatedAsyncStickyService service = new GatedAsyncStickyService();
        service.failFetches = true;
        GrowthBookClient client = new GrowthBookClient(stickyOptions(service));
        try {
            assertThrows(RuntimeException.class,
                    () -> client.run(experiment("exp-fail"), user("u1", "country")),
                    "a failing sticky store must not silently produce non-sticky assignments");
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
}
