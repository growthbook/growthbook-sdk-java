package growthbook.sdk.java.services;

import com.google.gson.JsonObject;
import growthbook.sdk.java.FeatureRule;
import growthbook.sdk.java.models.*;

import java.util.HashMap;

public class FeatureEvaluator implements IFeatureEvaluator {
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @Override
    public FeatureResult evaluateFeature(String key, Context context) {
        FeatureResult emptyFeature = FeatureResult
                .builder()
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
                        .builder()
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .rawJsonValue(feature.getDefaultValue())
                        .build();
            }

            HashMap<String, String> attributes = context.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            String attributesJson = GrowthBookJsonUtils.getInstance().gson.toJson(attributes);

            for (FeatureRule rule : feature.getRules()) {
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
                            .builder()
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

                Experiment experiment = Experiment
                        .builder()
                        .key(experimentKey)
                        .coverage(rule.getCoverage())
                        .weights(rule.getWeights())
                        .hashAttribute(rule.getHashAttribute())
                        .namespace(rule.getNamespace())
                        // TODO: Variations
                        .build();

                ExperimentResult result = experimentEvaluator.evaluateExperiment(experiment, context);

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
