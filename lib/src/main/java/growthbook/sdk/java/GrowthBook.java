package growthbook.sdk.java;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackAdapter;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackAdapter;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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

    private List<ExperimentRunCallback> callbacks;
    @Getter @Setter
    private JsonObject attributeOverrides;

    private JsonObject savedGroups;
    public EvaluationContext evaluationContext = null;
    private final Map<String, AssignedExperiment> assigned;

    @Getter @Setter private Map<String, Object> forcedFeatureValues;
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
        this.attributeOverrides = context.getAttributes() == null ? new JsonObject() : context.getAttributes();
        this.savedGroups = context.getSavedGroups() == null ? new JsonObject() : context.getSavedGroups();
        this.callbacks = new ArrayList<>();
        this.assigned = new HashMap<>();

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
        this.attributeOverrides = context.getAttributes() == null ? new JsonObject() : context.getAttributes();
        this.savedGroups = context.getSavedGroups() == null ? new JsonObject() : context.getSavedGroups();
        this.callbacks = new ArrayList<>();
        this.assigned = new HashMap<>();

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
        this.attributeOverrides = context.getAttributes() == null ? new JsonObject() : context.getAttributes();
        this.savedGroups = context.getSavedGroups() == null ? new JsonObject() : context.getSavedGroups();
        this.callbacks = new ArrayList<>();
        this.assigned = new HashMap<>();

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
                .forcedFeatureValues(this.forcedFeatureValues)
                .build();

        this.evaluationContext = new EvaluationContext(globalContext, userContext,
                new EvaluationContext.StackContext(), options);
    }

    private EvaluationContext getEvaluationContext() {
        // Reset the stackContext for every evaluation.
        this.evaluationContext.setStack(new EvaluationContext.StackContext());
        return this.evaluationContext;
    }

    /**
     * The evalFeature method takes a single string argument, which is the unique identifier for the feature and returns
     * a FeatureResult object.
     * <p>
     * There are a few ordered steps to evaluate a feature
     * <p>
     * 1. If the key doesn't exist in context.features
     *  1.1 Return getFeatureResult(null, "unknownFeature")
     * 2. Loop through the feature rules (if any)
     *  2.1 If the rule has parentConditions (prerequisites) defined, loop through each one:
     *      2.1.1 Call evalFeature on the parent condition
     *          2.1.1.1 If a cycle is detected, break out of feature evaluation and return getFeatureResult(null, "cyclicPrerequisite")
     *      2.1.2 Using the evaluated parent's result, create an object
     * @param key            name of the feature
     * @param valueTypeClass the class of the generic, e.g. MyFeature.class
     * @param <ValueType>    Gson deserializable type
     * @return ValueType instance
     */
    @Nullable
    @Override
    public <ValueType> FeatureResult<ValueType> evalFeature(String key, Class<ValueType> valueTypeClass) {
        return featureEvaluator.evaluateFeature(key, getEvaluationContext(), valueTypeClass);
    }

    /**
     * Method for pass feature json in format String to GbContext
     * @param featuresJsonString features JSON from the GrowthBook API
     */
    @Override
    public void setFeatures(String featuresJsonString) {
        this.context.setFeaturesJson(featuresJsonString);
        initializeEvalContext();
    }

    /**
     * Method for pass saved groups JsonObject to GbContext
     * @param savedGroups features JSON from the GrowthBook API
     */
    @Override
    public void setSavedGroups(JsonObject savedGroups) {
        this.context.setSavedGroups(savedGroups);
        this.savedGroups = savedGroups;
        initializeEvalContext();
    }

    /**
     * Update the user's attributes
     *
     * @param attributesJsonString user attributes JSON
     */
    @Override
    public void setAttributes(String attributesJsonString) {
        this.context.setAttributesJson(attributesJsonString);
        initializeEvalContext();
    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult.
     * There are a bunch of ordered steps to run an experiment:
     * 1. If experiment.variations has fewer than 2 variations, return getExperimentResult(experiment)
     * 2. If context.enabled is false, return getExperimentResult(experiment)
     * 3. If context.url exists
     * 4. Return if forced via context
     * 5. If experiment.active is set to false, return getExperimentResult(experiment)
     * 6. Get the user hash value and return if empty
     *  6.1 If sticky bucketing is permitted, check to see if a sticky bucket value exists. If so, skip steps 7-8.
     * 7. Apply filters and namespace
     *  7.1 If experiment.filters is set
     *  7.2 Else if experiment.namespace is set, return if not in range
     * 8. Return if any conditions are not met, return
     *  8.1 If experiment.condition is set, return if it evaluates to false
     *  8.2 If experiment.parentConditions is set (prerequisites), return if any of them evaluate to false. See the corresponding logic in evalFeature for more details. (Note that the gate flag should not be set in an experiment)
     *  8.3 Apply any url targeting based on experiment.urlPatterns, return if no match
     * 9. Choose a variation
     *  9.1 If a sticky bucket value exists, use it.
     *      9.1.1 If the found sticky bucket version is blocked (doesn't exceed experiment.minBucketVersion), then skip enrollment
     *  9.2 Else, calculate bucket ranges for the variations and choose one
     * 10. If assigned == -1, return getExperimentResult(experiment)
     * 11. If experiment has a forced variation, return
     * 12. If context.qaMode, return getExperimentResult(experiment)
     * 13. Build the result object
     * 14. Fire context.trackingCallback if set and the combination of hashAttribute, hashValue, experiment.key, and variationId has not been tracked before
     * 15. Return result
     *
     * @param experiment Experiment object
     * @return ExperimentResult instance
     * @param <ValueType> Gson deserializable type
     */
    @Override
    public <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment) {
        ExperimentResult<ValueType> result = experimentEvaluatorEvaluator
                .evaluateExperiment(experiment, getEvaluationContext(), null);

        fireSubscriptions(experiment, result);

        return result;
    }

    /**
     * Setting your own implementation of StickyBucketService interface
     *
     * @param stickyBucketService StickyBucketService
     */
    @Override
    public void setOwnStickyBucketService(@Nullable StickyBucketService stickyBucketService) {
        this.context.setStickyBucketService(stickyBucketService);
        initializeEvalContext();
    }

    /**
     * Setting default in memory implementation of StickyBucketService interface
     */
    @Override
    public void setInMemoryStickyBucketService() {
        this.context.setStickyBucketService(new InMemoryStickyBucketServiceImpl(new HashMap<>()));
        initializeEvalContext();
    }

    /**
     * Returns true if the value is a truthy value
     * Only the following values are considered to be "falsy":
     * 1) null
     * 2) false
     * 3) ""
     * 4) 0
     * Everything else is considered "truthy", including empty arrays and objects.
     * If the value is "truthy", then isOn() will return true and isOff() will return false.
     * If the value is "falsy", then the opposite values will be returned.
     *
     * @param featureKey name of the feature
     * @return true if the value is a truthy value
     */
    @Override
    public Boolean isOn(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvaluationContext(), Object.class).isOn();
    }

    /**
     * Returns true if the value is a falsy value
     * Only the following values are considered to be "falsy":
     * 1) null
     * 2) false
     * 3) ""
     * 4) 0
     * Everything else is considered "truthy", including empty arrays and objects.
     * If the value is "truthy", then isOn() will return true and isOff() will return false.
     * If the value is "falsy", then the opposite values will be returned.
     *
     * @param featureKey name of the feature
     * @return true if the value is a truthy value
     */
    @Override
    public Boolean isOff(String featureKey) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvaluationContext(), Object.class).isOff();
    }

    /**
     * Get the feature value as a boolean
     *
     * @param featureKey   name of the feature
     * @param defaultValue boolean value to return
     * @return the found value or defaultValue
     */
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

    /**
     * Get the feature value as a string
     *
     * @param featureKey   name of the feature
     * @param defaultValue string value to return
     * @return the found value or defaultValue
     */
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

    /**
     * Get the feature value as a float
     *
     * @param featureKey   name of the feature
     * @param defaultValue float value to return
     * @return the found value or defaultValue
     */
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

    /**
     * Get the feature value as an integer
     *
     * @param featureKey   name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
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

    /**
     * Get the feature value as an Object. This may be useful for implementations that do not use Gson.
     *
     * @param featureKey   feature identifier
     * @param defaultValue default object value
     * @return Object
     */
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

    /**
     * Get the feature value as a Gson-deserializable.
     * If your class requires a custom deserializer, use {@link #getFeatureValue(String, Object)} instead and deserialize it with your own Gson instance.
     *
     * @param featureKey              feature identifier
     * @param defaultValue            default generic class
     * @param gsonDeserializableClass the class of the generic, e.g. MyFeature.class
     * @param <ValueType>             Gson deserializable type
     * @return ValueType instance
     */
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

    /**
     * Evaluate a condition for a set of user attributes based on the provided condition.
     * The condition syntax closely resembles MongoDB's syntax.
     * This is defined in the Feature's targeting conditions' Advanced settings
     * <p>
     * This is the main function used to evaluate a condition. It loops through the condition key/value pairs and checks each entry:
     * <p>
     * 1. If condition key is $or, check if evalOr(attributes, condition["$or"]) is false. If so, break out of the loop and return false
     * 2. If condition key is $nor, check if !evalOr(attributes, condition["$nor"]) is false. If so, break out of the loop and return false
     * 3. If condition key is $and, check if evalAnd(attributes, condition["$and"]) is false. If so, break out of the loop and return false
     * 4. If condition key is $not, check if !evalCondition(attributes, condition["$not"]) is false. If so, break out of the loop and return false
     * 5. Otherwise, check if evalConditionValue(value, getPath(attributes, key)) is false. If so, break out of the loop and return false
     * If none of the entries failed their checks, evalCondition returns true
     * @param attributesJsonString A JsonObject of the user attributes to evaluate
     * @param conditionJsonString A JsonObject of the condition
     * @return Whether the condition should be true for the user
     */
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

    /**
     * Get the feature value as a double
     *
     * @param featureKey   name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
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

    /**
     * Reinitialized the list of ExperimentRunCallbacks.
     * This method clears the current list of callbacks by replacing it with a new empty ArrayList.
     */
    @Override
    public void destroy() {
        this.callbacks = new ArrayList<>();
    }

    /**
     * This method add new calback to list of ExperimentRunCallback
     * @param callback ExperimentRunCallback interface
     */
    @Override
    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }

    /**
     * Update sticky bucketing configuration
     * Method that get cached assignments
     * and set it to Context's Sticky Bucket Assignments documents
     * @param featuresDataModel Json in format of String. See info how it looks like here <a href="https://docs.growthbook.io/app/api#sdk-connection-endpoints">...</a>
     */
    @Override
    public void featuresAPIModelSuccessfully(String featuresDataModel) {
        refreshStickyBucketService(featuresDataModel);
    }

    /**
     * This method return boolean result if feature enabled by environment it would be present in context
     * @param featureKey Feature name
     * @return Whether feature is present in GBContext
     */
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

    private <ValueType> void fireSubscriptions(Experiment<ValueType> experiment, ExperimentResult<ValueType> result) {
        String key = experiment.getKey();
        // If assigned variation has changed, fire subscriptions
        AssignedExperiment prev = this.assigned.get(key);
        if (prev == null
                || !Objects.equals(prev.getExperimentResult().getInExperiment(), result.getInExperiment())
                || !Objects.equals(prev.getExperimentResult().getVariationId(), result.getVariationId())) {
            this.assigned.put(key, new AssignedExperiment<>(experiment, result));
            for (ExperimentRunCallback cb : this.callbacks) {
                try {
                    cb.onRun(experiment, result);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }
    }
}
