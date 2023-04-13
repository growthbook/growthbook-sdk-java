package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Initialize the GrowthBook SDK with a provided {@link GBContext}
     * @param context {@link GBContext}
     */
    public GrowthBook(GBContext context) {
        this.context = context;

        this.featureEvaluator = new FeatureEvaluator();
        this.conditionEvaluator = new ConditionEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
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
    }

    /**
     * <b>INTERNAL:</b> Constructor with injected dependencies. Useful for testing but not intended to be used
     *
     * @param context Context
     * @param featureEvaluator FeatureEvaluator
     * @param conditionEvaluator ConditionEvaluator
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
        return featureEvaluator.evaluateFeature(key, this.context, valueTypeClass);
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
    public <ValueType>ExperimentResult<ValueType> run(Experiment<ValueType> experiment) {
        ExperimentResult<ValueType> result = experimentEvaluatorEvaluator.evaluateExperiment(experiment, this.context, null);

        this.callbacks.forEach( callback -> {
            callback.onRun(result);
        });

        return result;
    }

    @Override
    public Boolean isOn(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, context, Object.class).isOn();
    }

    @Override
    public Boolean isOff(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, context, Object.class).isOff();
    }

    @Override
    public Boolean getFeatureValue(String featureKey, Boolean defaultValue) {
        try {
            Boolean maybeValue = (Boolean) this.featureEvaluator.evaluateFeature(featureKey, context, Boolean.class).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public String getFeatureValue(String featureKey, String defaultValue) {
        try {
            String maybeValue = (String) this.featureEvaluator.evaluateFeature(featureKey, context, String.class).getValue();
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
            Double maybeValue = (Double) this.featureEvaluator.evaluateFeature(featureKey, context, Double.class).getValue();

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
            // Type erasure occurs so a Double ends up being returned
            Double maybeValue = (Double) this.featureEvaluator.evaluateFeature(featureKey, context, Double.class).getValue();

            if (maybeValue == null) {
                return defaultValue;
            }

            try {
                return maybeValue.intValue();
            } catch (NumberFormatException e) {
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
            Object maybeValue = this.featureEvaluator.evaluateFeature(featureKey, context, defaultValue.getClass()).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    @Override
    public <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass) {
        try {
            Object maybeValue = this.featureEvaluator.evaluateFeature(featureKey, context, gsonDeserializableClass).getValue();
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
            Double maybeValue = (Double) this.featureEvaluator.evaluateFeature(featureKey, context, Double.class).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
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

    private Boolean isIncludedInRollout(
        String seed,
        String hashAttribute,
        @Nullable BucketRange range,
        @Nullable Float coverage,
        @Nullable HashVersion hashVersion
    ) {
        if (range == null && coverage == null) return true;

        if (hashAttribute == null || hashAttribute.equals("")) {
            hashAttribute = "id";
        }

        JsonObject attributes = context.getAttributes();
        if (attributes == null) return false;

        JsonElement hashValueElement = attributes.get(hashAttribute);
        if (hashValueElement == null || hashValueElement.isJsonNull()) return false;

        if (hashVersion == null) {
            hashVersion = HashVersion.V1;
        }
        String hashValue = hashValueElement.getAsString();
        Float hash = GrowthBookUtils.hash(hashValue, hashVersion, seed);
        if (hash == null) return false;

        Boolean isIncluded = GrowthBookUtils.inRange(hash, range);
        if (isIncluded) return true;

        if (coverage != null) return hash <= coverage;

        return true;
    }

    private Boolean isFilteredOut(List<Filter> filters) {
        if (filters == null) return false;

        JsonObject attributes = context.getAttributes();
        if (attributes == null) return false;

        return filters.stream().anyMatch(filter -> {
            if (filter.getAttribute() == null) return true;

            JsonElement hashValueElement = attributes.get(filter.getAttribute());
            if (hashValueElement == null) return true;
            if (hashValueElement.isJsonNull()) return true;
            if (!hashValueElement.isJsonPrimitive()) return true;

            JsonPrimitive hashValuePrimitive = hashValueElement.getAsJsonPrimitive();
            if (!hashValuePrimitive.isString()) return true;

            String hashValue = hashValuePrimitive.getAsString();
            if (hashValue == null || hashValue.equals("")) return true;

            HashVersion hashVersion = filter.getHashVersion();
            if (hashVersion == null) {
                hashVersion = HashVersion.V2;
            }

            Float n = GrowthBookUtils.hash(filter.getSeed(), hashVersion, hashValue);
            if (n == null) return true;

            List<BucketRange> ranges = filter.getRanges();
            if (ranges == null) return true;

            return ranges.stream().noneMatch(range -> GrowthBookUtils.inRange(n, range));
        });
    }
}
