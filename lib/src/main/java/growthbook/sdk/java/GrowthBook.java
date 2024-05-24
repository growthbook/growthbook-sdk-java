package growthbook.sdk.java;

import com.google.gson.JsonObject;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * GrowthBook SDK class.
 * Build a context with {@link GBContext#builder()} or the {@link GBContext} constructor
 * and pass it as an argument to the class constructor.
 */
public class GrowthBook implements IGrowthBook {

    private final GBContext context;

    // dependencies
    private final FeatureEvaluator featureEvaluator;
    private final ConditionEvaluator conditionEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    private ArrayList<ExperimentRunCallback> callbacks = new ArrayList<>();
    private JsonObject attributeOverrides = new JsonObject();

    /**
     * Initialize the GrowthBook SDK with a provided {@link GBContext}
     *
     * @param context {@link GBContext}
     */
    public GrowthBook(GBContext context) {
        this.context = context;

        this.featureEvaluator = new FeatureEvaluator();
        this.conditionEvaluator = new ConditionEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
        this.attributeOverrides = context.getAttributes();
    }

    /**
     * No-args constructor. A {@link GBContext} with default values is created.
     * It's recommended to create your own context with {@link GBContext#builder()} or the {@link GBContext} constructor
     */
    public GrowthBook() {
        this.context = GBContext.builder().build();

        // dependencies
        this.featureEvaluator = new FeatureEvaluator();
        this.conditionEvaluator = new ConditionEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
        this.attributeOverrides = context.getAttributes();
    }

    /**
     * <b>INTERNAL:</b> Constructor with injected dependencies. Useful for testing but not intended to be used
     *
     * @param context             Context
     * @param featureEvaluator    FeatureEvaluator
     * @param conditionEvaluator  ConditionEvaluator
     * @param experimentEvaluator ExperimentEvaluator
     */
    GrowthBook(GBContext context, FeatureEvaluator featureEvaluator, ConditionEvaluator conditionEvaluator, ExperimentEvaluator experimentEvaluator) {
        this.featureEvaluator = featureEvaluator;
        this.conditionEvaluator = conditionEvaluator;
        this.experimentEvaluatorEvaluator = experimentEvaluator;
        this.context = context;
    }

    @Nullable
    @Override
    public <ValueType> FeatureResult<ValueType> evalFeature(String key, Class<ValueType> valueTypeClass) {
        return featureEvaluator.evaluateFeature(key, this.context, valueTypeClass, attributeOverrides);
    }

    @Override
    public void setFeatures(String featuresJsonString) {
        this.context.setFeaturesJson(featuresJsonString);
    }

    @Override
    public void setAttributes(String attributesJsonString) {
        this.context.setAttributesJson(attributesJsonString);
    }

    @Override
    public <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment) {
        ExperimentResult<ValueType> result = experimentEvaluatorEvaluator.evaluateExperiment(experiment, this.context, null, attributeOverrides);

        this.callbacks.forEach(callback -> {
            callback.onRun(result);
        });

        return result;
    }

    @Override
    public void setOwnStickyBucketService(@Nullable StickyBucketService stickyBucketService) {
        this.context.setStickyBucketService(stickyBucketService);
    }

    @Override
    public void setInMemoryStickyBucketService() {
        this.context.setStickyBucketService(new InMemoryStickyBucketServiceImpl(new HashMap<>()));
    }

    @Override
    public Boolean isOn(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, context, Object.class, attributeOverrides).isOn();
    }

    @Override
    public Boolean isOff(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, context, Object.class, attributeOverrides).isOff();
    }

    @Override
    public Boolean getFeatureValue(String featureKey, Boolean defaultValue) {
        try {
            Boolean maybeValue = (Boolean) this.featureEvaluator.evaluateFeature(featureKey, context, Boolean.class, attributeOverrides).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public String getFeatureValue(String featureKey, String defaultValue) {
        try {
            String maybeValue = (String) this.featureEvaluator.evaluateFeature(featureKey, context, String.class, attributeOverrides).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Float getFeatureValue(String featureKey, Float defaultValue) {
        try {
            // Type erasure occurs so a Double ends up being returned
            Double maybeValue = (Double) this.featureEvaluator.evaluateFeature(featureKey, context, Double.class, attributeOverrides).getValue();

            if (maybeValue == null) {
                return defaultValue;
            }

            try {
                return maybeValue.floatValue();
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Integer getFeatureValue(String featureKey, Integer defaultValue) {
        try {
            Object maybeValue = this.featureEvaluator.evaluateFeature(featureKey, context, Object.class, attributeOverrides).getValue();

            if (maybeValue == null) {
                return defaultValue;
            }

            if (maybeValue instanceof Double) {
                return ((Double) maybeValue).intValue();
            } else if (maybeValue instanceof Long) {
                return ((Long) maybeValue).intValue();
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Object getFeatureValue(String featureKey, Object defaultValue) {
        try {
            Object maybeValue = this.featureEvaluator.evaluateFeature(featureKey, context, defaultValue.getClass(), attributeOverrides).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass) {
        try {
            Object maybeValue = this.featureEvaluator.evaluateFeature(featureKey, context, gsonDeserializableClass, attributeOverrides).getValue();
            if (maybeValue == null) {
                return defaultValue;
            }

            String stringValue = jsonUtils.gson.toJson(maybeValue);

            return jsonUtils.gson.fromJson(stringValue, gsonDeserializableClass);
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public Boolean evaluateCondition(String attributesJsonString, String conditionJsonString) {
        return conditionEvaluator.evaluateCondition(attributesJsonString, conditionJsonString);
    }

    @Override
    public Double getFeatureValue(String featureKey, Double defaultValue) {
        try {
            Object maybeValue = this.featureEvaluator.evaluateFeature(featureKey, context, Object.class, attributeOverrides).getValue();

            if (maybeValue == null) {
                return defaultValue;
            }

            if (maybeValue instanceof Double) {
                return (Double) maybeValue;
            } else if (maybeValue instanceof Long) {
                return ((Long) maybeValue).doubleValue();
            } else {
                return defaultValue;
            }
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

    @Override
    public void featuresAPIModelSuccessfully(String featuresDataModel) {
        refreshStickyBucketService(featuresDataModel);
    }

    private void refreshStickyBucketService(@Nullable String featuresDataModel) {
        if (context.getStickyBucketService() != null) {
            GrowthBookUtils.refreshStickyBuckets(context, featuresDataModel, attributeOverrides);
        }
    }
}
