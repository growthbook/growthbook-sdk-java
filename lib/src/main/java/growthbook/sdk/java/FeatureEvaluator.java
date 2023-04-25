package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * <b>INTERNAL</b>: Implementation of feature evaluation
 */
@Slf4j
class FeatureEvaluator implements IFeatureEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @Override
    public <ValueType> FeatureResult<ValueType> evaluateFeature(String key, GBContext context, Class<ValueType> valueTypeClass) throws ClassCastException {
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
                    return FeatureResult
                        .<ValueType>builder()
                        .value(forcedValue)
                        .source(FeatureResultSource.URL_OVERRIDE)
                        .build();
                }
            }

            // Unknown key, return empty feature
            JsonObject featuresJson = context.getFeatures();
            if (featuresJson == null || !featuresJson.has(key)) {
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
                log.debug("featureJson is null for [{}]", key);
                // When key exists but there is no value, should be default value with null value
                return defaultValueFeature;
            }

            Feature<ValueType> feature = jsonUtils.gson.fromJson(featureJson, Feature.class);
            if (feature == null) {
                // When key exists but there is no value, should be default value with null value
                return defaultValueFeature;
            }

            // If empty rule set, use the default value
            if (feature.getRules() == null || feature.getRules().isEmpty()) {
                ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(feature.getDefaultValue());
                return FeatureResult
                        .<ValueType>builder()
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .value(value)
                        .build();
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
                    return FeatureResult
                            .<ValueType>builder()
                            .value(value) // TODO: Check this. - This is not right
                            .source(FeatureResultSource.FORCE)
                            .build();
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

                    return FeatureResult
                            .<ValueType>builder()
                            .value(value)
                            .source(FeatureResultSource.EXPERIMENT)
                            .experiment(experiment)
                            .experimentResult(result)
                            .build();
                }
            }

            // endregion Rules

            ValueType value = (ValueType) GrowthBookJsonUtils.unwrap(feature.getDefaultValue());

            return FeatureResult
                    .<ValueType>builder()
                    .source(FeatureResultSource.DEFAULT_VALUE)
                    .value(value)
                    .build();
        } catch (Exception e) {
            log.error("Error evaluating feature [{}]", key, e);
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
            log.error("Error evaluating forced feature [{}] from URL [{}]", key, urlString, e);
            return null;
        }
    }
}
