package growthbook.sdk.java.util;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which experiment assignments have already been sent to the tracking callback.
 *
 * @deprecated No longer used by the SDK. Tracking de-duplication is handled by
 * {@link growthbook.sdk.java.multiusermode.ExperimentTracker}, which is shared by local and
 * remote evaluation. This class is kept only for binary compatibility and will be removed in a
 * future major release.
 */
@Deprecated
public class ExperimentHelper {
    private final Set<String> trackedExperiments = new HashSet<>();

    public <ValueType> boolean isTracked(Experiment<ValueType> experiment, ExperimentResult<ValueType> result) {
        String experimentKey = experiment.getKey();

        String key = (
                result.getHashAttribute() != null ? result.getHashAttribute() : "")
                + (result.getHashValue() != null ? result.getHashValue() : "")
                + (experimentKey + result.getVariationId());

        if (trackedExperiments.contains(key)) {
            return true;
        }
        trackedExperiments.add(key);
        return false;
    }
}
