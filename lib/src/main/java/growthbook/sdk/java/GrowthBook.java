package growthbook.sdk.java;

import growthbook.sdk.java.models.*;
import growthbook.sdk.java.services.ExperimentEvaluator;
import growthbook.sdk.java.services.FeatureEvaluator;
import growthbook.sdk.java.services.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;

/**
 * GrowthBook SDK class.
 * Build a context with {@link Context#builder()} or {@link Context#create(String, String, Boolean, Boolean, String, Map, TrackingCallback)}
 * and pass it as an argument to the class constructor.
 */
public class GrowthBook implements IGrowthBook {

    private final Context context;

    private final FeatureEvaluator featureEvaluator = new FeatureEvaluator();
    private final ExperimentEvaluator experimentEvaluatorEvaluator = new ExperimentEvaluator();

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    private ArrayList<ExperimentRunCallback> callbacks = new ArrayList<>();

    /**
     * Initialize the GrowthBook SDK with a provided {@link Context}
     * @param context {@link Context}
     */
    public GrowthBook(Context context) {
        this.context = context;
    }

    /**
     * No-args constructor. A {@link Context} with default values is created.
     * It's recommended to create your own context with {@link Context#builder()} or {@link Context#create(String, String, Boolean, Boolean, String, Map, TrackingCallback)}
     */
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
        this.context.setFeaturesJson(featuresJsonString);
    }

    @Override
    public <ValueType>ExperimentResult<ValueType> run(Experiment<ValueType> experiment) {
        ExperimentResult<ValueType> result = experimentEvaluatorEvaluator.evaluateExperiment(experiment, this.context, null);

        this.callbacks.forEach( callback -> {
            callback.onRun(result);
        });

        return result;
    }

    @Override
    public Boolean isOn(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, context).isOn();
    }

    @Override
    public Boolean isOff(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, context).isOff();
    }

    @Override
    public Boolean getFeatureValue(String featureKey, Boolean defaultValue) {
        try {
            return (Boolean) this.featureEvaluator.evaluateFeature(featureKey, context).getValue();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public String getFeatureValue(String featureKey, String defaultValue) {
        try {
            return (String) this.featureEvaluator.evaluateFeature(featureKey, context).getValue();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Float getFeatureValue(String featureKey, Float defaultValue) {
        try {
            Double value = getFeatureValue(featureKey, Double.valueOf(defaultValue));
            return value.floatValue();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Integer getFeatureValue(String featureKey, Integer defaultValue) {
        try {
            Double value = getFeatureValue(featureKey, Double.valueOf(defaultValue));
            return value.intValue();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Object getFeatureValue(String featureKey, Object defaultValue) {
        try {
            return this.featureEvaluator.evaluateFeature(featureKey, context).getValue();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass) {
        try {
            Object value = this.featureEvaluator.evaluateFeature(featureKey, context).getValue();
            String stringValue = jsonUtils.gson.toJson(value);

            return jsonUtils.gson.fromJson(stringValue, gsonDeserializableClass);
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Double getFeatureValue(String featureKey, Double defaultValue) {
        try {
            return (Double) this.featureEvaluator.evaluateFeature(featureKey, context).getValue();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public void destroy() {
        this.callbacks = new ArrayList<>();
    }

    @Override
    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }
}
