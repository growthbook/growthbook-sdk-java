package growthbook.sdk.java.services;

import com.google.gson.JsonElement;
import growthbook.sdk.java.FeatureRule;
import growthbook.sdk.java.models.*;

import java.util.HashMap;

public class FeatureEvaluator implements IFeatureEvaluator {
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @SuppressWarnings("unchecked")
    @Override
    public <ValueType> FeatureResult<ValueType> evaluateFeature(String key, Context context) throws ClassCastException {
        FeatureResult<ValueType> emptyFeature = FeatureResult
                .<ValueType>builder()
                .rawJsonValue(null)
                .on(false)
                .source(FeatureResultSource.UNKNOWN_FEATURE)
                .build();

        try {
            Feature feature = context.getFeatures().get(key);
            if (feature == null) {
                return emptyFeature;
            }

            // If empty rule set, use the default value
            if (feature.getRules().isEmpty()) {
                return FeatureResult
                        .<ValueType>builder()
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .rawJsonValue(feature.getDefaultValue())
                        .build();
            }

            HashMap<String, String> attributes = context.getAttributes();
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

                        String attrValue = attributes.get(ruleKey);
                        if (attrValue == null || attrValue.isEmpty()) {
                            continue;
                        }

                        Float hashFnv = GrowthBookUtils.hash(attrValue + key);
                        if (hashFnv > rule.getCoverage()) {
                            continue;
                        }
                    }

                    // Apply the force rule
                    return FeatureResult
                            .<ValueType>builder()
                            .rawJsonValue(rule.getForce()) // TODO: Check this.
                            .source(FeatureResultSource.FORCE)
                            .build();
                }

                // Experiment rule
                // TODO:
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
                        // TODO: Variations
                        .build();

                ExperimentResult<ValueType> result = experimentEvaluator.evaluateExperiment(experiment, context);
                if (result.getInExperiment()) {
                    JsonElement element = GrowthBookJsonUtils.getJsonElement(result.getValue());
                    String jsonString = "null";
                    if (element != null) {
                        jsonString = element.toString();
                    }

                    return FeatureResult
                            .<ValueType>builder()
                            .rawJsonValue(jsonString)
                            .source(FeatureResultSource.EXPERIMENT)
                            .build();
                }
                else {
                    continue;
                }

                // TODO: evaluate feature result
                // TODO: if result is in experiment, return a feature result of source experiment
                // else, continue;
            }

            return emptyFeature;

        } catch (Exception e) {
            e.printStackTrace();
            return emptyFeature;
        }
    }
}
