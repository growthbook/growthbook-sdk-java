package growthbook.sdk.java.multiusermode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An LRU Cache implementation to keep track of the most recent experiments.
 * Uses LinkedHashMap with access-order mode with a configurable size of 30.
 */
public class ExperimentTracker {
    private static final int MAX_EXPERIMENTS = 30;

    private final Map<String, Boolean> trackedExperiments;

    public ExperimentTracker() {
        // Create a thread-safe LRU cache with max 30 entries
        this.trackedExperiments = new LinkedHashMap<String, Boolean>(MAX_EXPERIMENTS, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_EXPERIMENTS;
            }
        };
    }

    public void trackExperiment(String experimentId) {
        trackedExperiments.put(experimentId, true);
    }

    public boolean isExperimentTracked(String experimentId) {
        return trackedExperiments.containsKey(experimentId);
    }

    public void clearTrackedExperiments() {
        trackedExperiments.clear();
    }
}
