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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
