package growthbook.sdk.java.multiusermode;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * A thread-safe LRU cache deduplicating experiment-exposure events by
 * {@code hashAttribute + hashValue + experimentKey + variationId} (the same
 * key composition as the JavaScript and Python SDKs).
 *
 * <p>Sizing matters: the multi-user client shares ONE tracker across all users,
 * so the bound must comfortably exceed the number of concurrently active unique
 * exposures — an undersized LRU evicts and re-fires exposures under load. Past
 * the bound, an evicted exposure may legitimately fire again.
 */
public class ExperimentTracker {
    private static final int MAX_EXPERIMENTS = 30;

    private final Cache<String, Boolean> trackedExperiments;

    /** Evaluator-local tracker with the legacy 30-entry bound (single-user scope). */
    public ExperimentTracker() {
        this(MAX_EXPERIMENTS);
    }

    /**
     * @param maxSize maximum tracked exposures before least-recently-used eviction
     */
    public ExperimentTracker(int maxSize) {
        this.trackedExperiments = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .build();
    }

    public void trackExperiment(String experimentId) {
        trackedExperiments.put(experimentId, Boolean.TRUE);
    }

    public boolean isExperimentTracked(String experimentId) {
        return trackedExperiments.getIfPresent(experimentId) != null;
    }

    /**
     * Un-marks an exposure whose delivery failed, so it is retried on the next
     * evaluation instead of being silently lost.
     *
     * @param experimentId the dedup key to un-mark
     */
    public void untrack(String experimentId) {
        trackedExperiments.invalidate(experimentId);
    }

    public void clearTrackedExperiments() {
        trackedExperiments.invalidateAll();
    }
}
