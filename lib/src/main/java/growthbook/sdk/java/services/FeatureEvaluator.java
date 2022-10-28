package growthbook.sdk.java.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.FeatureRule;
import growthbook.sdk.java.models.*;

import java.util.HashMap;

public class FeatureEvaluator implements IFeatureEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @SuppressWarnings("unchecked")
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
            System.out.printf("\n\nLooking for key %s in JSON %s", key, featuresJson);

            if (!featuresJson.has(key)) {
                System.out.printf("\n\nKey %s not found in JSON %s", key, featuresJson);
                return emptyFeature;
            }

            System.out.printf("\n\nKey %s exists in JSON %s", key, featuresJson);

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
                Object value = GrowthBookJsonUtils.unwrap(feature.getDefaultValue());
                return FeatureResult
                        .<ValueType>builder()
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .value(value)
                        .build();
            }

            HashMap<String, Object> attributes = context.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            String attributesJson = GrowthBookJsonUtils.getInstance().gson.toJson(attributes);

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

                        String attrValue = (String) attributes.get(ruleKey);
                        if (attrValue == null || attrValue.isEmpty()) {
                            continue;
                        }

                        Float hashFnv = GrowthBookUtils.hash(attrValue + key);
                        if (hashFnv > rule.getCoverage()) {
                            continue;
                        }
                    }

                    Object value = GrowthBookJsonUtils.unwrap(rule.getForce());

                    System.out.printf("🎃 Creating FeatureResult with raw JSON value %s", rule.getForce());
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

                System.out.printf("🎃 Creating FeatureResult with raw JSON value %s", feature.getDefaultValue());

                Experiment<ValueType> experiment = Experiment
                        .<ValueType>builder()
                        .key(experimentKey)
                        .coverage(rule.getCoverage())
                        .weights(rule.getWeights())
                        .hashAttribute(rule.getHashAttribute())
                        .namespace(rule.getNamespace())
                        .variations(rule.getVariations())
                        .build();

                ExperimentResult<ValueType> result = experimentEvaluator.evaluateExperiment(experiment, context);
                if (result.getInExperiment()) {
                    Object value = GrowthBookJsonUtils.unwrap(result.getValue());

                    return FeatureResult
                            .<ValueType>builder()
                            .value(value)
                            .ruleId(experimentKey) // todo: verify if this should be present
                            .source(FeatureResultSource.EXPERIMENT)
                            .experiment(experiment)
                            .experimentResult(result)
                            .build();
                }
            }

            System.out.printf("🎃 Creating FeatureResult with raw JSON value %s", feature.getDefaultValue());

            Object value = GrowthBookJsonUtils.unwrap(feature.getDefaultValue());

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
