package growthbook.sdk.java.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.FeatureRule;
import growthbook.sdk.java.models.*;

/**
 * <b>INTERNAL</b>: Implementation of feature evaluation
 */
public class FeatureEvaluator implements IFeatureEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @Override
    public <ValueType> FeatureResult<ValueType> evaluateFeature(String key, Context context) throws ClassCastException {
        FeatureResult<ValueType> emptyFeature = FeatureResult
                .<ValueType>builder()
                .value(null)
                .source(FeatureResultSource.UNKNOWN_FEATURE)
                .build();

        try {
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
                System.out.println("featureJson is null");
                // When key exists but there is no value, should be default value with null value
                return defaultValueFeature;
            }

//            System.out.printf("\n\nFeature: %s", featureJson);

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

                if (rule.getForce() != null) {
                    if (rule.getCoverage() != null) {
                        String ruleKey = rule.getHashAttribute();
                        if (ruleKey == null) {
                            ruleKey = "id";
                        }

                        JsonElement attrValueElement = attributes.get(ruleKey);

                        if (attrValueElement == null || attrValueElement.isJsonNull()) {
                            continue;
                        }

                        boolean isEmpty = false;
                        if (attrValueElement.isJsonObject()) {
                            isEmpty = attrValueElement.getAsJsonObject().entrySet().size() == 0;
                        } else if (attrValueElement.isJsonArray()) {
                            isEmpty = attrValueElement.getAsJsonArray().size() == 0;
                        } else if (attrValueElement.isJsonPrimitive() && attrValueElement.getAsJsonPrimitive().isString()) {
                            isEmpty = attrValueElement.getAsString().isEmpty();
                        }

                        if (isEmpty) {
                            continue;
                        }

                        String attrValue = attrValueElement.getAsString();
                        Float hashFnv = GrowthBookUtils.hash(attrValue + key);
                        if (hashFnv > rule.getCoverage()) {
                            continue;
                        }
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
                        .build();

                ExperimentResult<ValueType> result = experimentEvaluator.evaluateExperiment(experiment, context, key);
                if (result.getInExperiment()) {
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
            e.printStackTrace();
            return emptyFeature;
        }
    }
}
