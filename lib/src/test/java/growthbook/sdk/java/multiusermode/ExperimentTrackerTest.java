package growthbook.sdk.java.multiusermode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExperimentTrackerTest {

    @Test
    @DisplayName("Keeps only the 30 most recently tracked experiments")
    void keepsOnlyThirtyMostRecentExperiments() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();

        // When
        for (int i = 0; i < 35; i++) {
            tracker.trackExperiment("exp-" + i);
        }

        // Then
        assertEquals(trackedKeys(5, 35), trackedAmong(tracker, 0, 35));
    }

    @Test
    @DisplayName("Evicts the least recently used experiment, counting a check as a use")
    void evictsLeastRecentlyUsedExperiment() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        for (int i = 0; i < 30; i++) {
            tracker.trackExperiment("exp-" + i);
        }
        tracker.isExperimentTracked("exp-0");

        // When
        tracker.trackExperiment("exp-30");

        // Then
        assertTrue(tracker.isExperimentTracked("exp-0"));
        assertFalse(tracker.isExperimentTracked("exp-1"));
    }

    @Test
    @DisplayName("Marking an experiment reports it as new only the first time")
    void markingReportsNewOnlyFirstTime() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();

        // When
        boolean first = tracker.markTracked("exp-1");
        boolean second = tracker.markTracked("exp-1");

        // Then
        assertTrue(first);
        assertFalse(second);
        assertTrue(tracker.isExperimentTracked("exp-1"));
    }

    @Test
    @DisplayName("Marking treats different experiments independently")
    void markingTreatsExperimentsIndependently() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();

        // When
        boolean firstKey = tracker.markTracked("exp-1");
        boolean secondKey = tracker.markTracked("exp-2");

        // Then
        assertTrue(firstKey);
        assertTrue(secondKey);
    }

    @Test
    @DisplayName("Marking agrees with tracking and reports an experiment as new again after clearing")
    void markingAgreesWithTrackingAndClearing() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        tracker.trackExperiment("exp-1");

        // When
        boolean afterTrack = tracker.markTracked("exp-1");
        tracker.clearTrackedExperiments();
        boolean afterClear = tracker.markTracked("exp-1");

        // Then
        assertFalse(afterTrack);
        assertTrue(afterClear);
    }

    @Test
    @DisplayName("Marking reports an evicted experiment as new again")
    void markingReportsEvictedExperimentAsNew() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        for (int i = 0; i <= 30; i++) {
            tracker.markTracked("exp-" + i);
        }

        // When
        boolean evicted = tracker.markTracked("exp-0");

        // Then
        assertTrue(evicted);
    }

    @RepeatedTest(5)
    @DisplayName("Exactly one of many concurrent callers marking the same experiment sees it as new")
    void exactlyOneConcurrentCallerSeesExperimentAsNew() throws Exception {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        int newKeyCount = 0;

        try {
            // When
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return tracker.markTracked("exp-1");
                }));
            }
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    newKeyCount++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // Then
        assertEquals(1, newKeyCount);
    }

    @Test
    @Timeout(60)
    @DisplayName("Concurrent tracking, checking, marking and clearing never fails and keeps at most 30 experiments")
    void concurrentUseNeverFailsAndStaysBounded() throws Exception {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 32;
        int keys = 40;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> tasks = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        try {
            // When
            for (int t = 0; t < threads; t++) {
                int threadId = t;
                tasks.add(executor.submit(() -> {
                    barrier.await();
                    for (int i = 0; i < 5000; i++) {
                        String key = "exp-" + ((threadId + i) % keys);
                        if (i % 500 == 0) {
                            tracker.clearTrackedExperiments();
                        }
                        if (!tracker.isExperimentTracked(key)) {
                            tracker.trackExperiment(key);
                        }
                        tracker.markTracked(key);
                    }
                    return null;
                }));
            }
            for (Future<?> task : tasks) {
                try {
                    task.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // Then
        assertEquals(Collections.<Throwable>emptyList(), failures);
        assertTrue(trackedAmong(tracker, 0, keys).size() <= 30);
    }

    @RepeatedTest(5)
    @DisplayName("Claims an exposure key for exactly one of many concurrent callers")
    void claimsKeyForExactlyOneConcurrentCaller() throws Exception {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        int threads = 64;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        int claims = 0;

        try {
            // When
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return tracker.markIfFirst("hot") != null;
                }));
            }
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    claims++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // Then
        assertEquals(1, claims);
    }

    @Test
    @DisplayName("Releasing a claim after a failed delivery frees the key for exactly one retry")
    void releasingClaimAllowsSingleRetry() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        Object claim = tracker.markIfFirst("exp");
        Object concurrentClaim = tracker.markIfFirst("exp");

        // When
        tracker.untrack("exp", claim);

        // Then
        assertNotNull(claim);
        assertNull(concurrentClaim);
        assertNotNull(tracker.markIfFirst("exp"));
        assertNull(tracker.markIfFirst("exp"));
    }

    @Test
    @DisplayName("Releasing a stale claim leaves a newer claim of the same key in place")
    void releasingStaleClaimLeavesNewerClaim() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker();
        Object stale = tracker.markIfFirst("exp");
        tracker.clearTrackedExperiments();
        Object newer = tracker.markIfFirst("exp");

        // When
        tracker.untrack("exp", stale);

        // Then
        assertNotNull(newer);
        assertTrue(tracker.isExperimentTracked("exp"));
        assertNull(tracker.markIfFirst("exp"));
    }

    @ParameterizedTest(name = "maxSize {0}")
    @ValueSource(ints = {-1, 0})
    @DisplayName("Rejects a non-positive maximum size")
    void rejectsNonPositiveMaxSize(int maxSize) {
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new ExperimentTracker(maxSize));
    }

    @Test
    @DisplayName("A tracker of size two still de-duplicates the hot key and keeps at most two keys")
    void smallTrackerDedupesHotKeyWithinItsBound() {
        // Given
        ExperimentTracker tracker = new ExperimentTracker(2);
        Object first = tracker.markIfFirst("a");
        Object repeat = tracker.markIfFirst("a");

        // When
        for (int i = 0; i < 50; i++) {
            tracker.markIfFirst("exp-" + i);
        }

        // Then
        assertNotNull(first);
        assertNull(repeat);
        assertTrue(trackedAmong(tracker, 0, 50).size() <= 2);
    }

    private static List<String> trackedAmong(ExperimentTracker tracker, int fromInclusive, int toExclusive) {
        List<String> tracked = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (tracker.isExperimentTracked("exp-" + i)) {
                tracked.add("exp-" + i);
            }
        }
        return tracked;
    }

    private static List<String> trackedKeys(int fromInclusive, int toExclusive) {
        List<String> keys = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            keys.add("exp-" + i);
        }
        return keys;
    }
}
