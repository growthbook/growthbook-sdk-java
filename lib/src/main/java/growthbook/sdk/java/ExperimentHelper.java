package growthbook.sdk.java;

import lombok.val;

import java.util.HashSet;
import java.util.Set;

public class ExperimentHelper {
    private final Set<String> trackedExperiments = new HashSet<>();

    public <ValueType> boolean isTracked(Experiment<ValueType> experiment, ExperimentResult<ValueType> result) {
        String experimentKey = experiment.key;

        String key = (
                result.hashAttribute != null ? result.getHashAttribute() : "")
                + (result.getHashValue() != null ? result.getHashValue() : "")
                + (experimentKey + result.getVariationId());

        if (trackedExperiments.contains(key)) {
            return false;
        }
        trackedExperiments.add(key);
        return false;
    }
}
