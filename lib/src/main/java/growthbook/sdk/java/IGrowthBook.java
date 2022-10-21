package growthbook.sdk.java;

import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;
import growthbook.sdk.java.models.ExperimentRunCallback;
import growthbook.sdk.java.models.FeatureResult;

interface IGrowthBook {
    public <T> FeatureResult<T> evalFeature(String key);

    public ExperimentResult run(Experiment experiment);
    public Boolean isOn(String featureKey);
    public Boolean isOff(String featureKey);

    public void destroy();

    public void subscribe(ExperimentRunCallback callback);
    // TODO: getAllResults (not required)
}
