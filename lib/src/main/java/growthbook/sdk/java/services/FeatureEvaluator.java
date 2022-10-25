package growthbook.sdk.java.services;

import growthbook.sdk.java.FeatureRule;
import growthbook.sdk.java.models.*;

public class FeatureEvaluator implements IFeatureEvaluator {
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

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

            for (FeatureRule rule : feature.getRules()) {
                // If the rule has a condition, and it evaluates to false, skip this rule and continue to the next one
                if (rule.getCondition() != null) {
                    UserAttributes attributes = context.getAttributes();
                    String attributesString = attributes == null ? "{}" : attributes.toJson();
                    if (!conditionEvaluator.evaluateCondition(attributesString, rule.getCondition().toString())) {
                        continue;
                    }
                }

                // TODO: eval rule.force
                if (rule.getForce() != null) {
                    if (rule.getCoverage() != null) {
                        String ruleKey = rule.getHashAttribute();
                        if (ruleKey == null) {
                            ruleKey = "id";
                        }

                        // TODO: context.attributes.get(ruleKey)
                        // if empty, continue;
                        // else check hash. if hash is greater than rule.coverage, continue;
                        // continue;
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
            }

            // TODO: evaluate feature result

            return emptyFeature;

        } catch (Exception e) {
            e.printStackTrace();
            return emptyFeature;
        }
    }
}
