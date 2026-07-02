package growthbook.sdk.java.multiusermode;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * A thread-safe LRU Cache implementation to keep track of the most recent experiments.
 * Uses Guava cache with a maximum size of 30 entries.
 */
public class ExperimentTracker {
    private static final int MAX_EXPERIMENTS = 30;

    private final Cache<String, Boolean> trackedExperiments;

    public ExperimentTracker() {
        this.trackedExperiments = CacheBuilder.newBuilder()
                .maximumSize(MAX_EXPERIMENTS)
                .build();
    }

    public void trackExperiment(String experimentId) {
        trackedExperiments.put(experimentId, Boolean.TRUE);
    }

    public boolean isExperimentTracked(String experimentId) {
        return trackedExperiments.getIfPresent(experimentId) != null;
    }

    public void clearTrackedExperiments() {
        trackedExperiments.invalidateAll();
    }
}
