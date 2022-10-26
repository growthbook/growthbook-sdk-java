package growthbook.sdk.java;

import growthbook.sdk.java.models.*;
import growthbook.sdk.java.services.FeatureEvaluator;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class GrowthBook implements IGrowthBook {

    private Context context;

    private FeatureEvaluator featureEvaluator = new FeatureEvaluator();

    private ArrayList<ExperimentRunCallback> callbacks = new ArrayList<>();

    public GrowthBook(Context context) {
        this.context = context;
    }

    public GrowthBook() {
        this.context = Context.builder().build();
    }

    @Nullable
    @Override
    public <ValueType> FeatureResult<ValueType> evalFeature(String key) {
        return featureEvaluator.evaluateFeature(key, this.context);
    }

    @Override
    public void setFeatures(String featuresJsonString) {
        this.context.setFeatures(featuresJsonString);
    }

    @Override
    public <ValueType>ExperimentResult<ValueType> run(Experiment<ValueType> experiment) {
        // TODO:
//        ExperimentResult result = ExperimentResult

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
    public Boolean getFeatureValue(String featureKey, Boolean defaultValue) {
        // TODO: implement
        return defaultValue;
    }

    @Override
    public String getFeatureValue(String featureKey, String defaultValue) {
        // TODO: implement
        return defaultValue;
    }

    @Override
    public Float getFeatureValue(String featureKey, Float defaultValue) {
        // TODO: implement
        return defaultValue;
    }

    @Override
    public Integer getFeatureValue(String featureKey, Integer defaultValue) {
        // TODO: implement
        return defaultValue;
    }

    @Nullable
    @Override
    public String getRawFeatureValue(String featureKey) {
        // TODO: implement
        return null;
    }

    @Override
    public void destroy() {
        this.callbacks = new ArrayList<>();
    }

    @Override
    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }

    // TODO: private getFeatureResult(value, source, experiment, experimentResult): FeatureResult
    // TODO: private getExperimentResult(experiment, variationIndex, hashUsed, featureId): ExperimentResult
    // TODO: getFeatureValue(key, defaultValue) // defaultValue is the fallback value
}
