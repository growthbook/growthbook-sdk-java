package growthbook.sdk.java.evaluators;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

interface IFeatureEvaluator {
    /**
     * Evaluate a feature.
     *
     * @param key string key for the feature
     * @param context GrowthBook context
     * @param <ValueType> the type of value for the variations
     * @return feature result
     * @throws ClassCastException When a value type fails to cast to the provided type, this can throw an exception
     */
    <ValueType> FeatureResult<ValueType> evaluateFeature(String key, EvaluationContext context, Class<ValueType> valueTypeClass);
}
