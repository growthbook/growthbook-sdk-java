package growthbook.sdk.java.multiusermode;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A thread-safe LRU cache deduplicating experiment-exposure events by
 * {@code hashAttribute + hashValue + experimentKey + variationId} (the same
 * key composition as the JavaScript and Python SDKs).
 *
 * <p>One tracker belongs to one SDK instance and is shared by all its evaluations, so an
 * exposure is reported once per instance no matter which entry point produced it.
 *
 * <p>Sizing matters: the multi-user client shares ONE tracker across all users,
 * so the bound must comfortably exceed the number of concurrently active unique
 * exposures — an undersized LRU evicts and re-fires exposures under load. Past
 * the bound, an evicted exposure may legitimately fire again.
 */
public class ExperimentTracker {
    private static final int MAX_EXPERIMENTS = 30;

    /** Each entry's value is the claim of the caller that marked it; see {@link #markIfFirst}. */
    private final Map<String, Object> trackedExperiments;

    /** Tracker with the legacy 30-entry bound (single-user scope). */
    public ExperimentTracker() {
        this(MAX_EXPERIMENTS);
    }

    /**
     * @param maxSize maximum tracked exposures before least-recently-used eviction; must be at least 1
     * @throws IllegalArgumentException if {@code maxSize} is less than 1
     */
    public ExperimentTracker(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be at least 1, was " + maxSize);
        }
        this.trackedExperiments = new LinkedHashMap<String, Object>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized void trackExperiment(String experimentId) {
        trackedExperiments.put(experimentId, Boolean.TRUE);
    }

    public synchronized boolean isExperimentTracked(String experimentId) {
        return trackedExperiments.get(experimentId) != null;
    }

    /**
     * Marks an experiment as tracked in one atomic step.
     *
     * @param experimentId the tracking key
     * @return {@code true} if the key was not tracked before this call, so exactly one of several
     * concurrent callers with the same key gets {@code true}
     */
    public synchronized boolean markTracked(String experimentId) {
        return markIfFirst(experimentId) != null;
    }

    /**
     * Atomically claims an exposure for delivery: marks it tracked and reports
     * whether this caller was the first to do so. Concurrent first evaluations of
     * the same key on a shared tracker see exactly one claim, so the exposure is
     * dispatched once rather than racing a separate check-then-mark.
     *
     * @param experimentId the dedup key to claim
     * @return this caller's claim, to hand to {@link #untrack} if delivery fails,
     *         or {@code null} if the key was already tracked
     */
    @Nullable
    public synchronized Object markIfFirst(String experimentId) {
        if (trackedExperiments.get(experimentId) != null) {
            return null;
        }
        Object claim = new Object();
        trackedExperiments.put(experimentId, claim);
        return claim;
    }

    /**
     * Un-marks an exposure whose delivery failed, so it is retried on the next
     * evaluation instead of being silently lost. Only the mark made by
     * {@code claim} is removed: if the key was evicted and claimed again while
     * the failed delivery was still running, the newer mark stays.
     *
     * @param experimentId the dedup key to un-mark
     * @param claim        the value {@link #markIfFirst} returned for this delivery
     */
    public synchronized void untrack(String experimentId, Object claim) {
        trackedExperiments.remove(experimentId, claim);
    }

    public synchronized void clearTrackedExperiments() {
        trackedExperiments.clear();
    }
}
