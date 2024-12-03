package growthbook.sdk.java;

import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackAdapter;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackAdapter;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * GrowthBook SDK class.
 * Build a context with {@link GBContext#builder()} or the {@link GBContext} constructor
 * and pass it as an argument to the class constructor.
 */
@Slf4j
public class GrowthBook implements IGrowthBook {

    private final GBContext context;

    // dependencies
    private final FeatureEvaluator featureEvaluator;
    private final ConditionEvaluator conditionEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    private ArrayList<ExperimentRunCallback> callbacks = new ArrayList<>();
    private JsonObject attributeOverrides = new JsonObject();
    private JsonObject savedGroups = new JsonObject();
    public EvaluationContext evaluationContext = null;

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
        this.savedGroups = context.getSavedGroups();

        this.initializeEvalContext();
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
        this.savedGroups = context.getSavedGroups();

        this.initializeEvalContext();
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

        this.initializeEvalContext();
    }

    private void initializeEvalContext() {
        // build options
        Options options = Options.builder()
                .enabled(this.context.getEnabled())
                .isQaMode(this.context.getIsQaMode())
                .allowUrlOverrides(this.context.getAllowUrlOverride())
                .url(this.context.getUrl())
                .stickyBucketIdentifierAttributes(this.context.getStickyBucketIdentifierAttributes())
                .stickyBucketService(this.context.getStickyBucketService())
                .trackingCallBackWithUser(new TrackingCallbackAdapter(this.context.getTrackingCallback()))
                .featureUsageCallbackWithUser(new FeatureUsageCallbackAdapter(this.context.getFeatureUsageCallback()))
                .build();

        // build global
        GlobalContext globalContext = GlobalContext.builder()
                .features(this.context.getFeatures())
                .savedGroups(this.context.getSavedGroups())
                .build();

        // build user context
        UserContext userContext = UserContext.builder()
                .attributes(this.context.getAttributes())
                .stickyBucketAssignmentDocs(this.context.getStickyBucketAssignmentDocs())
                .forcedVariationsMap(this.context.getForcedVariationsMap())
                .build();

        this.evaluationContext = new EvaluationContext(globalContext, userContext,
                new EvaluationContext.StackContext(), options);
    }

    private EvaluationContext getEvaluationContext() {
        // Reset the stackContext for every evaluation.
        this.evaluationContext.setStack(new EvaluationContext.StackContext());
        return this.evaluationContext;
    }

    @Nullable
    @Override
    public <ValueType> FeatureResult<ValueType> evalFeature(String key, Class<ValueType> valueTypeClass) {
        return featureEvaluator.evaluateFeature(key, getEvaluationContext(), valueTypeClass);
    }

    @Override
    public void setFeatures(String featuresJsonString) {
        this.context.setFeaturesJson(featuresJsonString);
    }

    @Override
    public void setSavedGroups(JsonObject savedGroups) {
        this.context.setSavedGroups(savedGroups);
    }

    @Override
    public void setAttributes(String attributesJsonString) {
        this.context.setAttributesJson(attributesJsonString);
    }

    @Override
    public <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment) {
        ExperimentResult<ValueType> result = experimentEvaluatorEvaluator
                .evaluateExperiment(experiment, getEvaluationContext(), null);

        this.callbacks.forEach(callback -> callback.onRun(result));

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
        return this.featureEvaluator.evaluateFeature(featureKey, getEvaluationContext(), Object.class).isOn();
    }

    @Override
    public Boolean isOff(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvaluationContext(), Object.class).isOff();
    }

    @Override
    public Boolean getFeatureValue(String featureKey, Boolean defaultValue) {
        try {
            Boolean maybeValue = (Boolean) this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), Boolean.class).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    @Override
    public String getFeatureValue(String featureKey, String defaultValue) {
        try {
            String maybeValue = (String) this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), String.class).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    @Override
    public Float getFeatureValue(String featureKey, Float defaultValue) {
        try {
            // Type erasure occurs so a Double ends up being returned
            Object maybeValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), Object.class).getValue();

            if (maybeValue == null) {
                return defaultValue;
            }

            if (maybeValue instanceof Double) {
                return ((Double) maybeValue).floatValue();
            } else if (maybeValue instanceof Long) {
                return ((Long) maybeValue).floatValue();
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    @Override
    public Integer getFeatureValue(String featureKey, Integer defaultValue) {
        try {
            Object maybeValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), Object.class).getValue();

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
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    @Override
    public Object getFeatureValue(String featureKey, Object defaultValue) {
        try {
            Object maybeValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), defaultValue.getClass()).getValue();
            return maybeValue == null ? defaultValue : maybeValue;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    @Override
    public <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass) {
        try {
            Object maybeValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), gsonDeserializableClass).getValue();
            if (maybeValue == null) {
                return defaultValue;
            }

            String stringValue = jsonUtils.gson.toJson(maybeValue);

            return jsonUtils.gson.fromJson(stringValue, gsonDeserializableClass);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    @Override
    public Boolean evaluateCondition(String attributesJsonString, String conditionJsonString) {
        try {
            JsonObject attributesJson = jsonUtils.gson.fromJson(attributesJsonString, JsonObject.class);
            JsonObject conditionJson = jsonUtils.gson.fromJson(conditionJsonString, JsonObject.class);
            JsonObject savedGroupsJson = jsonUtils.gson.fromJson(savedGroups, JsonObject.class);
            return conditionEvaluator.evaluateCondition(attributesJson, conditionJson, savedGroupsJson);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Double getFeatureValue(String featureKey, Double defaultValue) {
        try {
            Object maybeValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvaluationContext(), Object.class).getValue();

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
            log.error(e.getMessage(), e);
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

    // if feature enabled by environment it would be present in context
    @Override
    public Boolean isFeatureEnabled(String featureKey) {
        if (context.getFeatures() != null) {
            return context.getFeatures().keySet().contains(featureKey);
        }
        return false;
    }

    private void refreshStickyBucketService(@Nullable String featuresDataModel) {
        if (context.getStickyBucketService() != null) {
            GrowthBookUtils.refreshStickyBuckets(context, featuresDataModel, attributeOverrides);
        }
    }
}
