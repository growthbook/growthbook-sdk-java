package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Implementation of feature evaluation.
 * Takes Context and Feature Key.
 * Returns Calculated Feature Result against that key
 */
@Slf4j
public class FeatureEvaluator implements IFeatureEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();
    //private final FeatureEvalContext featureEvalContext = new FeatureEvalContext(null, new HashSet<>());

    // Takes Context and Feature Key
    // Returns Calculated Feature Result against that key
    @Override
    public <ValueType> FeatureResult<ValueType> evaluateFeature(
            String key,
            EvaluationContext context,
            Class<ValueType> valueTypeClass
    ) throws ClassCastException {
        // This callback serves for listening for feature usage events
        FeatureUsageCallbackWithUser featureUsageCallbackWithUser = context.getOptions()
                .getFeatureUsageCallbackWithUser();

        FeatureResult<ValueType> emptyFeature = FeatureResult
                .<ValueType>builder()
                .value(null)
                .source(FeatureResultSource.UNKNOWN_FEATURE)
                .build();

        try {
            if (context.getStack().getEvaluatedFeatures().contains(key)) {
                // block that handle recursion
                log.info(
                        "evaluateFeature: circular dependency detected: {} -> {}. { from: {}, to: {} }",
                        context.getStack().getId(), key,
                        context.getStack().getId(), key
                );

                FeatureResult<ValueType> featureResultWhenCircularDependencyDetected = FeatureResult
                        .<ValueType>builder()
                        .value(null)
                        .source(FeatureResultSource.CYCLIC_PREREQUISITE)
                        .build();
                if (featureUsageCallbackWithUser != null) {
                    featureUsageCallbackWithUser.onFeatureUsage(key, featureResultWhenCircularDependencyDetected, context.getUser());
                }
                
                leaveCircularLoop(context);
                return featureResultWhenCircularDependencyDetected;
            }

            // Check for feature values forced by URL
            if (context.getOptions().getAllowUrlOverrides()) {
                ValueType forcedValue = evaluateForcedFeatureValueFromUrl(key, context.getOptions().getUrl(), valueTypeClass);
                if (forcedValue != null) {
                    FeatureResult<ValueType> urlFeatureResult = FeatureResult
                            .<ValueType>builder()
                            .value(forcedValue)
                            .source(FeatureResultSource.URL_OVERRIDE)
                            .build();

                    if (featureUsageCallbackWithUser != null) {
                        featureUsageCallbackWithUser.onFeatureUsage(key, urlFeatureResult, context.getUser());
                    }

                    return urlFeatureResult;
                }
            }

            // Unknown key, return empty feature
            JsonObject features = context.getGlobal().getFeatures();
            if (features == null || !features.has(key)) {
                if (featureUsageCallbackWithUser != null) {
                    featureUsageCallbackWithUser.onFeatureUsage(key, emptyFeature, context.getUser());
                }

                return emptyFeature;
            }

            // The key exists
            JsonElement featureJson = features.get(key);
            FeatureResult<ValueType> defaultValueFeature = FeatureResult
                    .<ValueType>builder()
                    .value(null)
                    .source(FeatureResultSource.DEFAULT_VALUE)
                    .build();

            if (featureJson == null) {
                log.info("featureJson is null");

                // When key exists but there is no value, should be default value with null value
                if (featureUsageCallbackWithUser != null) {
                    featureUsageCallbackWithUser.onFeatureUsage(key, defaultValueFeature, context.getUser());
                }

                return defaultValueFeature;
            }

            Feature<ValueType> feature = jsonUtils.gson.fromJson(featureJson, Feature.class);
            if (feature == null) {
                // When key exists but there is no value, should be default value with null value
                if (featureUsageCallbackWithUser != null) {
                    featureUsageCallbackWithUser.onFeatureUsage(key, defaultValueFeature, context.getUser());
                }
                return defaultValueFeature;
            }

            // If empty rule set, use the default value
            if (feature.getRules() == null || feature.getRules().isEmpty()) {
                ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(feature.getDefaultValue());
                FeatureResult<ValueType> defaultValueFeatureForRules = FeatureResult
                        .<ValueType>builder()
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .value(value)
                        .build();
                if (featureUsageCallbackWithUser != null) {
                    featureUsageCallbackWithUser.onFeatureUsage(key, defaultValueFeatureForRules, context.getUser());
                }
                return defaultValueFeatureForRules;
            }

            /*String attributesJson = context.getAttributesJson();
            if (attributesJson == null) {
                attributesJson = "{}";
            }
            JsonObject attributes = context.getAttributes();
            if (attributes == null) {
                attributes = new JsonObject();
            }*/

            // Loop through the feature rules (if any)
            for (FeatureRule<ValueType> rule : feature.getRules()) {
                // If there are prerequisite flag(s), evaluate them
                if (rule.getParentConditions() != null) {
                    for (ParentCondition parentCondition : rule.getParentConditions()) {
                        enterCircularLoop(key, context);
                        FeatureResult<ValueType> parentResult = evaluateFeature(
                                parentCondition.getId(),
                                context,
                                valueTypeClass);

                        // break out for cyclic prerequisites
                        if (parentResult.getSource() != null) {
                            if (parentResult.getSource().equals(FeatureResultSource.CYCLIC_PREREQUISITE)) {
                                FeatureResult<ValueType> featureResultWhenCircularDependencyDetected =
                                        FeatureResult
                                                .<ValueType>builder()
                                                .value(null)
                                                .source(FeatureResultSource.CYCLIC_PREREQUISITE)
                                                .build();

                                if (featureUsageCallbackWithUser != null) {
                                    featureUsageCallbackWithUser.onFeatureUsage(key,
                                            featureResultWhenCircularDependencyDetected,
                                            context.getUser());
                                }
                                return featureResultWhenCircularDependencyDetected;
                            }
                        }

                        Map<String, Object> evalObj = new HashMap<>();
                        if (parentResult.getValue() != null) {
                            evalObj.put("value", parentResult.getValue());
                        }
                        JsonObject parentAttributesJson = GrowthBookJsonUtils.getInstance().gson.toJsonTree(evalObj).getAsJsonObject();

                        boolean evalCondition = conditionEvaluator.evaluateCondition(
                                parentAttributesJson,
                                parentCondition.getCondition(),
                                context.getGlobal().getSavedGroups()
                        );

                        // blocking prerequisite eval failed: feature evaluation fails
                        if (!evalCondition) {
                            // blocking prerequisite eval failed: feature evaluation fails
                            if (parentCondition.getGate()) {
                                log.info("Feature blocked by prerequisite");

                                FeatureResult<ValueType> featureResultWhenBlockedByPrerequisite =
                                        FeatureResult
                                                .<ValueType>builder()
                                                .value(null)
                                                .source(FeatureResultSource.PREREQUISITE)
                                                .build();

                                if (featureUsageCallbackWithUser != null) {
                                    featureUsageCallbackWithUser.onFeatureUsage(key,
                                            featureResultWhenBlockedByPrerequisite,
                                            context.getUser());
                                }
                                return featureResultWhenBlockedByPrerequisite;
                            }
                            // non-blocking prerequisite eval failed: break out
                            // of parentConditions loop, jump to the next rule
                        }
                    }
                }

                // If there are filters for who is included (e.g. namespaces)
                List<Filter> filters = rule.getFilters();
                if (GrowthBookUtils.isFilteredOut(filters, context.getUser().getAttributes())) {

                    // Skip rule because of filters
                    continue;
                }

                // Feature value is being forced
                if (rule.getForce() != null) {

                    // If the rule has a condition, and it evaluates to false, skip this rule and continue to the next one
                    if (rule.getCondition() != null) {
                        if (!conditionEvaluator.evaluateCondition(context.getUser().getAttributes(),
                                rule.getCondition(), context.getGlobal().getSavedGroups())) {

                            // Skip rule because of condition
                            continue;
                        }
                    }

                    boolean gate1 = context.getOptions().getStickyBucketService() != null;
                    boolean gate2 = !Boolean.TRUE.equals(rule.disableStickyBucketing);
                    boolean shouldFallbackAttributeBePassed = gate1 && gate2;

                    // Pass fallback attribute if sticky bucketing is enabled.
                    String fallback = shouldFallbackAttributeBePassed ? rule.getFallbackAttribute() : null;

                    String ruleKey = rule.getHashAttribute();
                    if (ruleKey == null) {
                        ruleKey = "id";
                    }

                    String seed = rule.getSeed();
                    if (seed == null) {
                        seed = key;
                    }

                    // If this is a percentage rollout, skip if not included
                    if (
                            !GrowthBookUtils.isIncludedInRollout(
                                    context.getUser().getAttributes(),
                                    seed,
                                    ruleKey,
                                    fallback,
                                    rule.getRange(),
                                    rule.getCoverage(),
                                    rule.getHashVersion()
                            )
                    ) {

                        // Skip rule because user not included in rollout
                        continue;
                    }

                    // Call the tracking callback with all the track data
                    List<TrackData<ValueType>> trackData = rule.getTracks();
                    TrackingCallbackWithUser trackingCallBackWithUser = context.getOptions().getTrackingCallBackWithUser();

                    // If this was a remotely evaluated experiment, fire the tracking callbacks
                    if (trackData != null && trackingCallBackWithUser != null) {
                        trackData.forEach(t -> trackingCallBackWithUser.onTrack(t.getExperiment(), t.getExperimentResult(), context.getUser()));
                    }

                    if (rule.getRange() == null) {
                        if (rule.getCoverage() != null) {
//                            String key = ruleKey;

                            String attributeValue = context.getUser().getAttributes().get(ruleKey) == null
                                    ? null : context.getUser().getAttributes().get(ruleKey).getAsString();
                            
                            if (attributeValue == null || attributeValue.isEmpty()) {
                                continue;
                            }

                            Float hashFNV = GrowthBookUtils.hash(attributeValue, 1, key);
                            if (hashFNV == null) {
                                hashFNV = 0f;
                            }
                            if (hashFNV > rule.getCoverage()) {
                                continue;
                            }
                        }
                    }

                    ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(rule.getForce());

                    // Apply the force rule
                    FeatureResult<ValueType> forcedRuleFeatureValue = FeatureResult
                            .<ValueType>builder()
                            .value(value) // TODO: Check this. - This is not right
                            .source(FeatureResultSource.FORCE)
                            .build();

                    if (featureUsageCallbackWithUser != null) {
                        featureUsageCallbackWithUser.onFeatureUsage(key, forcedRuleFeatureValue, context.getUser());
                    }
                    
                    return forcedRuleFeatureValue;
                } else {

                    ArrayList<ValueType> variations = rule.getVariations();
                    if (variations != null) {

                        // Experiment rule
                        String experimentKey = rule.getKey();
                        if (experimentKey == null) {
                            experimentKey = key;
                        }

                        // For experiment rules, run an experiment
                        Experiment<ValueType> experiment = Experiment
                                .<ValueType>builder()
                                .key(experimentKey)
                                .variations(variations)
                                .coverage(rule.getCoverage())
                                .weights(rule.getWeights())
                                .hashAttribute(rule.getHashAttribute())
                                .fallbackAttribute(rule.getFallbackAttribute())
                                .disableStickyBucketing(rule.getDisableStickyBucketing())
                                .bucketVersion(rule.getBucketVersion())
                                .minBucketVersion(rule.getMinBucketVersion())
                                .namespace(rule.getNamespace())
                                .meta(rule.getMeta())
                                .ranges(rule.getRanges())
                                .name(rule.getName())
                                .phase(rule.getPhase())
                                .seed(rule.getSeed())
                                .hashVersion(rule.getHashVersion())
                                .filters(rule.getFilters())
                                .conditionJson(rule.getCondition())
                                .parentConditions(rule.getParentConditions())
                                .build();

                        // Only return a value if the user is part of the experiment
                        ExperimentResult<ValueType> result = experimentEvaluator.evaluateExperiment(experiment, context, key);
                        if (result.getInExperiment() && (result.getPassThrough() == null || !result.getPassThrough())) {
                            ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(result.getValue());

                            FeatureResult<ValueType> experimentFeatureResult = FeatureResult
                                    .<ValueType>builder()
                                    .value(value)
                                    .source(FeatureResultSource.EXPERIMENT)
                                    .experiment(experiment)
                                    .experimentResult(result)
                                    .build();

                            if (featureUsageCallbackWithUser != null) {
                                featureUsageCallbackWithUser.onFeatureUsage(key, experimentFeatureResult, context.getUser());
                            }
                            return experimentFeatureResult;
                        }
                    } else {
                        continue;
                    }
                }
            }

            // endregion Rules

            ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(feature.getDefaultValue());

            FeatureResult<ValueType> defaultValueFeatureResult = FeatureResult
                    .<ValueType>builder()
                    .source(FeatureResultSource.DEFAULT_VALUE)
                    .value(value)
                    .build();

            if (featureUsageCallbackWithUser != null) {
                featureUsageCallbackWithUser.onFeatureUsage(key, defaultValueFeatureResult, context.getUser());
            }

            // Return (value = defaultValue or null, source = defaultValue)
            return defaultValueFeatureResult;
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage(), e);

            // If the key doesn't exist in context.features, return immediately
            // (value = null, source = unknownFeature).
            return emptyFeature;
        }
    }

    private @Nullable <ValueType> ValueType evaluateForcedFeatureValueFromUrl(String key, @Nullable String urlString, Class<ValueType> valueTypeClass) {
        if (urlString == null) return null;

        try {
            URL url = new URL(urlString);

            if (valueTypeClass.equals(Boolean.class)) {
                return (ValueType) GrowthBookUtils.getForcedBooleanValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(String.class)) {
                return (ValueType) GrowthBookUtils.getForcedStringValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(Integer.class)) {
                return (ValueType) GrowthBookUtils.getForcedIntegerValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(Float.class)) {
                return (ValueType) GrowthBookUtils.getForcedFloatValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(Double.class)) {
                return (ValueType) GrowthBookUtils.getForcedDoubleValueFromUrl(key, url);
            }

            return GrowthBookUtils.getForcedSerializableValueFromUrl(key, url, valueTypeClass, jsonUtils.gson);
        } catch (MalformedURLException | ClassCastException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }
    
    private void enterCircularLoop(String featureKey, EvaluationContext context) {
        context.getStack().getEvaluatedFeatures().add(featureKey);
        context.getStack().setId(featureKey);
    }
    
    private void leaveCircularLoop(EvaluationContext context) {
        context.getStack().setId(null);
        context.getStack().getEvaluatedFeatures().clear();
    }
}
