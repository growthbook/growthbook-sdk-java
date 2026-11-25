package growthbook.sdk.java.multiusermode;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentTrackerTest {

    /**
     * Test 1: Basic concurrent access
     * Simulates multiple threads tracking and checking experiments at the same time.
     */
    @RepeatedTest(5)
    void testConcurrentAccessBasic() throws InterruptedException {
        ExperimentTracker tracker = new ExperimentTracker();

        int threads = 50;
        int iterations = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    String key = "exp-" + ThreadLocalRandom.current().nextInt(100);
                    tracker.trackExperiment(key);
                    tracker.isExperimentTracked(key);
                }
            } catch (Throwable t) {
                failed.set(true);
                t.printStackTrace();
            }
        };

        for (int i = 0; i < threads; i++) executor.submit(task);
        executor.shutdown();
        boolean finished = executor.awaitTermination(60, TimeUnit.SECONDS);

        assertTrue(finished, "Executor did not finish in time");
        assertFalse(failed.get(), "Concurrent access caused exception");
    }

    /**
     * Test 2: LRU eviction
     * Ensures that the cache does not grow beyond its limit (max 30 experiments).
     */
    @Test
    void testLRUEviction() {
        ExperimentTracker tracker = new ExperimentTracker();

        for (int i = 0; i < 35; i++) tracker.trackExperiment("exp-" + i);

        int count = 0;
        for (int i = 0; i < 35; i++) {
            if (tracker.isExperimentTracked("exp-" + i)) count++;
        }

        assertTrue(count <= 30, "Cache should contain at most 30 experiments");
    }

    /**
     * Test 3: Concurrent clear and put
     * Simulates threads clearing the cache while others are adding/checking experiments.
     */
    @RepeatedTest(5)
    void testConcurrentClearAndPut() throws InterruptedException {
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < 20; i++) tracker.trackExperiment("init-" + i);

        Runnable task = () -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    if (i % 100 == 0) tracker.clearTrackedExperiments();
                    String key = "exp-" + (i % 10);
                    tracker.trackExperiment(key);
                    tracker.isExperimentTracked(key);
                    if (i % 50 == 0) Thread.yield();
                }
            } catch (Throwable t) {
                failed.set(true);
                System.err.println("❌ Clear+Put race condition: " + t.getClass().getSimpleName());
            }
        };

        for (int i = 0; i < threads; i++) executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertFalse(failed.get(), "Concurrent clear and put operations caused race condition");
    }

    /**
     * Test 4: Realistic production-like usage
     * Simulates typical GrowthBook usage with check-then-act pattern in multiple threads.
     */
    @RepeatedTest(5)
    void testRealisticProductionPattern() throws InterruptedException {
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                for (int i = 0; i < 5000; i++) {
                    String key = "feature-flag-" + (i % 20);
                    if (!tracker.isExperimentTracked(key)) tracker.trackExperiment(key);
                    tracker.isExperimentTracked(key);
                    if (i % 100 == 0) Thread.yield();
                }
            } catch (Throwable t) {
                failed.set(true);
                t.printStackTrace();
            }
        };

        for (int i = 0; i < threads; i++) executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        assertFalse(failed.get(), "Production-like pattern revealed race condition");
    }

    /**
     * Test 5: High contention with small key set
     * Forces threads to work on very few keys to increase chance of race condition.
     */
    @RepeatedTest(5)
    void testHighContentionSmallKeys() throws InterruptedException {
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 20;
        int iterations = 50_000;
        String[] keys = {"hot1", "hot2", "hot3"};

        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                barrier.await();
                for (int i = 0; i < iterations; i++) {
                    String key = keys[i % keys.length];
                    tracker.trackExperiment(key);
                    tracker.isExperimentTracked(key);
                    if (i % 100 == 0) Thread.yield();
                }
            } catch (Throwable t) {
                failed.set(true);
                System.err.println("❌ Exception: " + t.getClass().getSimpleName());
            }
        };
        for (int i = 0; i < threads; i++) executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        assertFalse(failed.get(), "High-contention small-key test revealed race condition");
    }

    /**
     * Test 6: markTracked reports only the first call per key
     */
    @Test
    void testMarkTrackedReturnsTrueOnlyForFirstCall() {
        ExperimentTracker tracker = new ExperimentTracker();

        boolean first = tracker.markTracked("exp-1");
        boolean second = tracker.markTracked("exp-1");

        assertTrue(first, "First markTracked call should report the key as new");
        assertFalse(second, "Second markTracked call should report the key as already tracked");
        assertTrue(tracker.isExperimentTracked("exp-1"));
    }

    /**
     * Test 7: markTracked treats different keys independently
     */
    @Test
    void testMarkTrackedTreatsKeysIndependently() {
        ExperimentTracker tracker = new ExperimentTracker();

        boolean firstKey = tracker.markTracked("exp-1");
        boolean secondKey = tracker.markTracked("exp-2");

        assertTrue(firstKey);
        assertTrue(secondKey);
    }

    /**
     * Test 8: markTracked agrees with trackExperiment and clearTrackedExperiments
     */
    @Test
    void testMarkTrackedAfterTrackExperimentAndClear() {
        ExperimentTracker tracker = new ExperimentTracker();
        tracker.trackExperiment("exp-1");

        boolean afterTrack = tracker.markTracked("exp-1");
        tracker.clearTrackedExperiments();
        boolean afterClear = tracker.markTracked("exp-1");

        assertFalse(afterTrack, "A key tracked through trackExperiment should not be new");
        assertTrue(afterClear, "A key should be new again after clearing");
    }

    /**
     * Test 9: markTracked reports an evicted key as new again
     */
    @Test
    void testMarkTrackedAfterEviction() {
        ExperimentTracker tracker = new ExperimentTracker();
        tracker.markTracked("exp-0");
        for (int i = 1; i <= 30; i++) tracker.markTracked("exp-" + i);

        boolean evicted = tracker.markTracked("exp-0");

        assertTrue(evicted, "The least recently used key should have been evicted");
    }

    /**
     * Test 10: markTracked is atomic under concurrent callers
     * Exactly one of many threads released at once for the same key gets true.
     */
    @RepeatedTest(5)
    void testMarkTrackedConcurrentCallersSameKey() throws InterruptedException, ExecutionException {
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return tracker.markTracked("exp-1");
                }));
            }

            int newKeyCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) newKeyCount++;
            }

            assertEquals(1, newKeyCount, "Exactly one concurrent caller should see the key as new");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Test 11: Atomic claim under concurrent first evaluations.
     * The old check-then-mark let two threads both pass isExperimentTracked before either
     * called trackExperiment, so both dispatched the exposure — and a failure in one then
     * untracked the other's successful delivery. markIfFirst must hand a key to exactly one
     * caller, even when every thread races on it simultaneously.
     */
    @RepeatedTest(50)
    void markIfFirstClaimsAKeyForExactlyOneConcurrentCaller() throws Exception {
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 64;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger claims = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                if (tracker.markIfFirst("hot") != null) {
                    claims.incrementAndGet();
                }
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, claims.get(), "exactly one concurrent caller may claim a given exposure key");
    }

    /**
     * Test 12: A failed delivery releases only its own claim, and the retry is single-use.
     * Mirrors the success/failure interleave of trackExposure: the winner claims, a loser is
     * turned away, an untrack after a failed dispatch frees the key for exactly one retry.
     */
    @Test
    void untrackReleasesOnlyTheClaimItOwnsAndAllowsASingleRetry() {
        ExperimentTracker tracker = new ExperimentTracker();

        Object claim = tracker.markIfFirst("exp");
        assertNotNull(claim, "first caller claims the exposure");
        assertNull(tracker.markIfFirst("exp"), "a concurrent loser must not also dispatch");

        tracker.untrack("exp", claim);

        assertNotNull(tracker.markIfFirst("exp"), "a failed delivery frees the key for retry");
        assertNull(tracker.markIfFirst("exp"), "the retried exposure is claimed only once");
    }

    /**
     * Test 7b: A claim that outlived its entry cannot release a newer one. A slow delivery's
     * key can be evicted and claimed again before the slow delivery fails.
     */
    @Test
    void untrackWithAStaleClaimLeavesANewerClaimInPlace() {
        ExperimentTracker tracker = new ExperimentTracker();

        Object stale = tracker.markIfFirst("exp");
        tracker.clearTrackedExperiments(); // stands in for LRU eviction while the delivery runs
        Object newer = tracker.markIfFirst("exp");
        assertNotNull(newer, "an evicted key can be claimed again");

        tracker.untrack("exp", stale);

        assertTrue(tracker.isExperimentTracked("exp"), "the newer, delivered exposure must stay marked");
        assertNull(tracker.markIfFirst("exp"));
    }

    /**
     * Test 13: Sizing boundaries for the ExperimentTracker(int) constructor.
     * A dedup cache must hold at least one entry, so a non-positive bound is rejected; the
     * smallest valid bound still dedupes a hot key and never retains more than its size.
     */
    @Test
    void constructorRejectsNonPositiveMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new ExperimentTracker(-1));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentTracker(0));
    }

    @Test
    void smallCacheDedupesTheHotKeyAndStaysWithinItsBound() {
        ExperimentTracker tracker = new ExperimentTracker(2);

        assertNotNull(tracker.markIfFirst("a"), "first claim of a key wins");
        assertNull(tracker.markIfFirst("a"), "the just-claimed key is deduped");

        for (int i = 0; i < 50; i++) {
            tracker.markIfFirst("k-" + i);
        }
        int retained = 0;
        for (int i = 0; i < 50; i++) {
            if (tracker.isExperimentTracked("k-" + i)) {
                retained++;
            }
        }
        assertTrue(retained <= 2, "a size-2 cache must not retain more than its bound");
    }
}
