package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <b>INTERNAL</b>: Implementation of feature evaluation
 */
class FeatureEvaluator implements IFeatureEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();
    private final Logger logger = Logger.getAnonymousLogger();

    @Override
    public <ValueType> FeatureResult<ValueType> evaluateFeature(String key, GBContext context, Class<ValueType> valueTypeClass) throws ClassCastException {
        FeatureUsageCallback featureUsageCallback = context.getFeatureUsageCallback();

        FeatureResult<ValueType> emptyFeature = FeatureResult
                .<ValueType>builder()
                .value(null)
                .source(FeatureResultSource.UNKNOWN_FEATURE)
                .build();

        try {
            // Check for feature values forced by URL
            if (context.getAllowUrlOverride()) {
                ValueType forcedValue = evaluateForcedFeatureValueFromUrl(key, context.getUrl(), valueTypeClass);
                if (forcedValue != null) {
                    FeatureResult<ValueType> urlFeatureResult = FeatureResult
                        .<ValueType>builder()
                        .value(forcedValue)
                        .source(FeatureResultSource.URL_OVERRIDE)
                        .build();

                    if (featureUsageCallback != null) {
                        featureUsageCallback.onFeatureUsage(key, urlFeatureResult);
                    }

                    return urlFeatureResult;
                }
            }

            // Unknown key, return empty feature
            JsonObject featuresJson = context.getFeatures();
            if (featuresJson == null || !featuresJson.has(key)) {
                if (featureUsageCallback != null) {
                    featureUsageCallback.onFeatureUsage(key, emptyFeature);
                }

                return emptyFeature;
            }

            // The key exists
            JsonElement featureJson = featuresJson.get(key);
            FeatureResult<ValueType> defaultValueFeature = FeatureResult
                    .<ValueType>builder()
                    .value(null)
                    .source(FeatureResultSource.DEFAULT_VALUE)
                    .build();

            if (featureJson == null) {
                logger.log(Level.CONFIG, "FeatureJson is null for ", key);

                // When key exists but there is no value, should be default value with null value
                if (featureUsageCallback != null) {
                    featureUsageCallback.onFeatureUsage(key, defaultValueFeature);
                }

                return defaultValueFeature;
            }

            Feature<ValueType> feature = jsonUtils.gson.fromJson(featureJson, Feature.class);
            if (feature == null) {
                // When key exists but there is no value, should be default value with null value
                if (featureUsageCallback != null) {
                    featureUsageCallback.onFeatureUsage(key, defaultValueFeature);
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
                if (featureUsageCallback != null) {
                    featureUsageCallback.onFeatureUsage(key, defaultValueFeatureForRules);
                }
                return defaultValueFeatureForRules;
            }

            String attributesJson = context.getAttributesJson();
            if (attributesJson == null) {
                attributesJson = "{}";
            }
            JsonObject attributes = context.getAttributes();
            if (attributes == null) {
                attributes = new JsonObject();
            }
//            System.out.printf("\n\nAttributes = %s", attributes);
            logger.log(Level.INFO, "Attributes = ", attributes);

            // region Rules

            for (FeatureRule<ValueType> rule : feature.getRules()) {
                // If the rule has a condition, and it evaluates to false, skip this rule and continue to the next one
                if (rule.getCondition() != null) {
                    if (!conditionEvaluator.evaluateCondition(attributesJson, rule.getCondition().toString())) {
                        continue;
                    }
                }

                // If there are filters for who is included (e.g. namespaces)
                List<Filter> filters = rule.getFilters();
                if (GrowthBookUtils.isFilteredOut(filters, attributes)) {
                    continue;
                }

                if (rule.getForce() != null) {
                    String ruleKey = rule.getHashAttribute();
                    if (ruleKey == null) {
                        ruleKey = "id";
                    }

                    String seed = rule.getSeed();
                    if (seed == null) {
                        seed = key;
                    }

                    if (
                        !GrowthBookUtils.isIncludedInRollout(
                            attributes,
                            seed,
                            ruleKey,
                            rule.getRange(),
                            rule.getCoverage(),
                            rule.getHashVersion()
                        )
                    ) {
                        continue;
                    }

                    // Call the tracking callback with all the track data
                    List<TrackData<ValueType>> trackData = rule.getTracks();
                    TrackingCallback trackingCallback = context.getTrackingCallback();
                    if (trackData != null && trackingCallback != null) {
                        trackData.forEach(t -> {
                            trackingCallback.onTrack(t.getExperiment(), t.getExperimentResult());
                        });
                    }

                    ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(rule.getForce());

                    // Apply the force rule
                    FeatureResult<ValueType> forcedRuleFeatureValue = FeatureResult
                            .<ValueType>builder()
                            .value(value) // TODO: Check this. - This is not right
                            .source(FeatureResultSource.FORCE)
                            .build();

                    if (featureUsageCallback != null) {
                        featureUsageCallback.onFeatureUsage(key, forcedRuleFeatureValue);
                    }
                    return forcedRuleFeatureValue;
                }

                // Experiment rule
                String experimentKey = rule.getKey();
                if (experimentKey == null) {
                    experimentKey = key;
                }

                Experiment<ValueType> experiment = Experiment
                        .<ValueType>builder()
                        .key(experimentKey)
                        .coverage(rule.getCoverage())
                        .weights(rule.getWeights())
                        .hashAttribute(rule.getHashAttribute())
                        .namespace(rule.getNamespace())
                        .variations(rule.getVariations())
                        .meta(rule.getMeta())
                        .ranges(rule.getRanges())
                        .name(rule.getName())
                        .phase(rule.getPhase())
                        .seed(rule.getSeed())
                        .hashVersion(rule.getHashVersion())
                        .filters(rule.getFilters())
                        .build();

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

                    if (featureUsageCallback != null) {
                        featureUsageCallback.onFeatureUsage(key, experimentFeatureResult);
                    }
                    return experimentFeatureResult;
                }
            }

            // endregion Rules

            ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(feature.getDefaultValue());

            FeatureResult<ValueType> defaultValueFeatureResult = FeatureResult
                    .<ValueType>builder()
                    .source(FeatureResultSource.DEFAULT_VALUE)
                    .value(value)
                    .build();

            if (featureUsageCallback != null) {
                featureUsageCallback.onFeatureUsage(key, defaultValueFeatureResult);
            }
            return defaultValueFeatureResult;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error evaluating feature " +  key, e);
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
            logger.log(Level.SEVERE, "Error evaluating forced feature "
                    +  key + " from URL " + urlString, e);
            return null;
        }
    }
}
