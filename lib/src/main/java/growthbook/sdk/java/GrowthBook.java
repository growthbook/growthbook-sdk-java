package growthbook.sdk.java;

import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;
import growthbook.sdk.java.models.ExperimentRunCallback;
import growthbook.sdk.java.models.FeatureResult;

public class GrowthBook implements IGrowthBook {

    @Override
    public <T> FeatureResult<T> evalFeature(String key) {
        // TODO:
        return null;
    }

    @Override
    public ExperimentResult run(Experiment experiment) {
        // TODO:
        return null;
    }

    @Override
    public Boolean isOn(String featureKey) {
        // TODO:
        return null;
    }

    @Override
    public Boolean isOff(String featureKey) {
        // TODO:
        return null;
    }

    @Override
    public void destroy() {
        // TODO:
    }

    @Override
    public void subscribe(ExperimentRunCallback callback) {
        // TODO:
    }

    // TODO: private getFeatureResult(value, source, experiment, experimentResult): FeatureResult
    // TODO: private getExperimentResult(experiment, variationIndex, hashUsed, featureId): ExperimentResult
    // TODO: getFeatureValue(key, defaultValue) // defaultValue is the fallback value
}
