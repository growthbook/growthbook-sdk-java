package growthbook.sdk.java.multiusermode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thread-safe LRU Cache implementation to keep track of the most recent experiments.
 * Holds at most 30 entries, evicting the least recently used one.
 *
 * <p>One tracker belongs to one SDK instance and is shared by all its evaluations, so an
 * exposure is reported once per instance no matter which entry point produced it.
 */
public class ExperimentTracker {
    private static final int MAX_EXPERIMENTS = 30;

    private final Map<String, Boolean> trackedExperiments = new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_EXPERIMENTS;
        }
    };

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
        return trackedExperiments.put(experimentId, Boolean.TRUE) == null;
    }

    public synchronized void clearTrackedExperiments() {
        trackedExperiments.clear();
    }
}
