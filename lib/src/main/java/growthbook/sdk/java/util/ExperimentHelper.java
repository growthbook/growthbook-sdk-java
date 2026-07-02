package growthbook.sdk.java.util;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;

import java.util.HashSet;
import java.util.Set;

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
