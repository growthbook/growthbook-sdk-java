package growthbook.sdk.java.perf;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded harness demonstrating sticky bucket I/O behavior under load,
 * mirroring the Python SDK's {@code tests/scripts/benchmark_async_client.py}
 * scenarios. A simulated store latency (default 5 ms) stands in for Redis.
 *
 * <p>Run with: {@code ./gradlew :lib:runStickyPerfHarness}
 * (properties: -PstickyPerfThreads, -PstickyPerfRequests, -PstickyPerfLatencyMs)
 *
 * <p>Context for the numbers: before the async sticky work, every evaluation
 * performed one BLOCKING getAllAssignments per eval call on the request thread
 * (plus a blocking save per new assignment), so throughput was capped at
 * threads / latency with zero overlap. The scenarios below show the same work
 * with offloaded+coalesced reads, the async API, and per-request prefetch.
 */
public final class StickyBucketPerfHarness {

    private static final int THREADS = Integer.getInteger("stickyPerf.threads", 100);
    private static final int REQUESTS = Integer.getInteger("stickyPerf.requests", 2_000);
    private static final long LATENCY_MS = Long.getLong("stickyPerf.latencyMs", 5L);
    private static final int EVALS_PER_REQUEST_PREFETCH = 10;

    private StickyBucketPerfHarness() {
    }

    /** Async store completing after a scheduled delay — no threads parked. */
    private static final class SimulatedAsyncStore implements AsyncStickyBucketService {
        private final Map<String, StickyAssignmentsDocument> store = new ConcurrentHashMap<>();
        private final ScheduledExecutorService timer;

        SimulatedAsyncStore(ScheduledExecutorService timer) {
            this.timer = timer;
        }

        private <T> CompletionStage<T> delayed(T value) {
            CompletableFuture<T> future = new CompletableFuture<>();
            timer.schedule(() -> future.complete(value), LATENCY_MS, TimeUnit.MILLISECONDS);
            return future;
        }

        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return delayed(store.get(getKey(attributeName, attributeValue)));
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
            store.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
            return delayed(null);
        }

        @Override
        public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
            Map<String, StickyAssignmentsDocument> result = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                StickyAssignmentsDocument doc = store.get(getKey(attribute.getKey(), attribute.getValue()));
                if (doc != null) {
                    result.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                }
            }
            return delayed(result);
        }
    }

    /** Blocking store: one Thread.sleep round trip per batched call (offloaded by the client). */
    private static final class SimulatedSyncStore implements StickyBucketService {
        private final Map<String, StickyAssignmentsDocument> store = new ConcurrentHashMap<>();

        private void roundTrip() {
            try {
                Thread.sleep(LATENCY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            roundTrip();
            return store.get(attributeName + "||" + attributeValue);
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            roundTrip();
            store.put(doc.getAttributeName() + "||" + doc.getAttributeValue(), doc);
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            roundTrip();
            Map<String, StickyAssignmentsDocument> result = new ConcurrentHashMap<>();
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                StickyAssignmentsDocument doc = store.get(attribute.getKey() + "||" + attribute.getValue());
                if (doc != null) {
                    result.put(doc.getAttributeName() + "||" + doc.getAttributeValue(), doc);
                }
            }
            return result;
        }
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

    public static void main(String[] args) throws Exception {
        System.out.printf("threads=%d requests=%d simulated store latency=%dms%n%n",
                THREADS, REQUESTS, LATENCY_MS);

        ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "sim-store-timer");
            thread.setDaemon(true);
            return thread;
        });

        runSyncApiScenario("sync store / sync API / distinct users",
                Options.builder().stickyBucketService(new SimulatedSyncStore())
                        .stickyBucketIdentifierAttributes(Collections.singletonList("id")).build(),
                true);
        runSyncApiScenario("async store / sync API / distinct users",
                asyncOptions(timer), true);
        runSyncApiScenario("async store / sync API / hot user (coalesced)",
                asyncOptions(timer), false);
        runAsyncApiScenario("async store / async API / distinct users", asyncOptions(timer), timer);
        runPrefetchScenario("async store / prefetch + " + EVALS_PER_REQUEST_PREFETCH + " evals per request",
                asyncOptions(timer));

        timer.shutdownNow();
    }

    private static Options asyncOptions(ScheduledExecutorService timer) {
        return Options.builder()
                .asyncStickyBucketService(new SimulatedAsyncStore(timer))
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build();
    }

    /** THREADS request threads each running blocking evaluations. */
    private static void runSyncApiScenario(String name, Options options, boolean distinctUsers) throws Exception {
        GrowthBookClient client = new GrowthBookClient(options);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>(REQUESTS));
        AtomicInteger next = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(THREADS);
        long started = System.nanoTime();
        for (int t = 0; t < THREADS; t++) {
            Thread worker = new Thread(() -> {
                int i;
                while ((i = next.getAndIncrement()) < REQUESTS) {
                    String userId = distinctUsers ? "user-" + i : "hot-user";
                    long begin = System.nanoTime();
                    client.run(experiment("exp-perf"), user(userId));
                    latenciesNanos.add(System.nanoTime() - begin);
                }
                done.countDown();
            });
            worker.setDaemon(true);
            worker.start();
        }
        done.await(10, TimeUnit.MINUTES);
        long elapsed = System.nanoTime() - started;
        client.shutdown();
        report(name, elapsed, latenciesNanos);
    }

    /** One caller thread pipelining async evaluations with bounded in-flight. */
    private static void runAsyncApiScenario(String name, Options options, ScheduledExecutorService timer) throws Exception {
        GrowthBookClient client = new GrowthBookClient(options);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>(REQUESTS));
        Semaphore inflight = new Semaphore(THREADS);
        CountDownLatch done = new CountDownLatch(REQUESTS);
        long started = System.nanoTime();
        for (int i = 0; i < REQUESTS; i++) {
            inflight.acquire();
            long begin = System.nanoTime();
            client.runAsync(experiment("exp-perf"), user("user-" + i))
                    .whenComplete((result, error) -> {
                        latenciesNanos.add(System.nanoTime() - begin);
                        inflight.release();
                        done.countDown();
                    });
        }
        done.await(10, TimeUnit.MINUTES);
        long elapsed = System.nanoTime() - started;
        client.shutdown();
        report(name, elapsed, latenciesNanos);
    }

    /** JS-style pattern: prefetch once per request, then N evals with zero sticky I/O. */
    private static void runPrefetchScenario(String name, Options options) throws Exception {
        GrowthBookClient client = new GrowthBookClient(options);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>(REQUESTS));
        AtomicInteger next = new AtomicInteger();
        AtomicLong evals = new AtomicLong();
        CountDownLatch done = new CountDownLatch(THREADS);
        long started = System.nanoTime();
        for (int t = 0; t < THREADS; t++) {
            Thread worker = new Thread(() -> {
                int i;
                while ((i = next.getAndIncrement()) < REQUESTS) {
                    long begin = System.nanoTime();
                    UserContext prefetched = client.prefetchStickyBuckets(user("user-" + i)).join();
                    for (int e = 0; e < EVALS_PER_REQUEST_PREFETCH; e++) {
                        client.run(experiment("exp-" + e), prefetched);
                        evals.incrementAndGet();
                    }
                    latenciesNanos.add(System.nanoTime() - begin);
                }
                done.countDown();
            });
            worker.setDaemon(true);
            worker.start();
        }
        done.await(10, TimeUnit.MINUTES);
        long elapsed = System.nanoTime() - started;
        client.shutdown();
        System.out.printf("  (%d total evaluations across %d requests)%n", evals.get(), REQUESTS);
        report(name, elapsed, latenciesNanos);
    }

    private static void report(String name, long elapsedNanos, List<Long> latenciesNanos) {
        List<Long> sorted = new ArrayList<>(latenciesNanos);
        Collections.sort(sorted);
        double seconds = elapsedNanos / 1_000_000_000.0;
        double throughput = sorted.size() / seconds;
        System.out.printf("%-55s %,9.0f req/s  p50=%.1fms p95=%.1fms p99=%.1fms%n",
                name, throughput,
                percentileMs(sorted, 0.50), percentileMs(sorted, 0.95), percentileMs(sorted, 0.99));
    }

    private static double percentileMs(List<Long> sortedNanos, double percentile) {
        if (sortedNanos.isEmpty()) {
            return 0;
        }
        int index = Math.min(sortedNanos.size() - 1, (int) Math.floor(percentile * sortedNanos.size()));
        return sortedNanos.get(index) / 1_000_000.0;
    }
}
