package growthbook.sdk.java;

import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;
import growthbook.sdk.java.models.ExperimentRunCallback;

public interface IGrowthBook {
    // TODO: Maybe not make it generic
//    public FeatureResult evalFeature(String key, );
    // TODO: public evalFeature(key: string): FeatureResult

    public ExperimentResult run(Experiment experiment);
    public Boolean isOn(String featureKey);
    public Boolean isOff(String featureKey);

    public void destroy();

    public void subscribe(ExperimentRunCallback callback);
    // TODO: getAllResults (not required)
}
