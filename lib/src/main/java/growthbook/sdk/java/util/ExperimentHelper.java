package growthbook.sdk.java.util;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class ExperimentHelper {
    private final Set<String> trackedExperiments = new HashSet<>();

    public <ValueType> boolean isTracked(Experiment<ValueType> experiment, @Nullable ExperimentResult<ValueType> result) {
        String experimentKey = experiment.getKey();
        String hashAttribute = (result != null && result.getHashAttribute() != null) ? result.getHashAttribute() : "";
        String hashValue = (result != null && result.getHashValue() != null) ? result.getHashValue() : "";
        String variationId = (result != null) ? String.valueOf(result.getVariationId()) : "";

        String key = hashAttribute + hashValue + experimentKey + variationId;

        if (trackedExperiments.contains(key)) {
            return true;
        }
        trackedExperiments.add(key);
        return false;
    }
}
