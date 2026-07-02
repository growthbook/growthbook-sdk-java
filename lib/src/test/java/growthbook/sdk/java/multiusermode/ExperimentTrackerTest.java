package growthbook.sdk.java.multiusermode;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
}
