package growthbook.sdk.java.multiusermode;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentTrackerTest {

    @Test
    void testThreadSafetyUnderConcurrentAccess() throws InterruptedException {
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 50;
        int iterations = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        Runnable writerReaderTask = () -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    String id = "exp-" + ThreadLocalRandom.current().nextInt(100);
                    tracker.trackExperiment(id);
                    tracker.isExperimentTracked(id);
                }
            } catch (Throwable t) {
                failed.set(true);
                t.printStackTrace();
            }
        };

        for (int i = 0; i < threads; i++) {
            executor.submit(writerReaderTask);
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(60, TimeUnit.SECONDS);

        assertTrue(finished, "Executor did not finish in time");
        assertFalse(failed.get(), "Concurrent access caused exception");
    }

    @Test
    void testLRUEviction() {
        ExperimentTracker tracker = new ExperimentTracker();

        for (int i = 0; i < 35; i++) {
            tracker.trackExperiment("exp-" + i);
        }

        int count = 0;
        for (int i = 0; i < 35; i++) {
            if (tracker.isExperimentTracked("exp-" + i)) count++;
        }

        assertTrue(count <= 30, "Cache should contain at most 30 experiments");
    }
}
