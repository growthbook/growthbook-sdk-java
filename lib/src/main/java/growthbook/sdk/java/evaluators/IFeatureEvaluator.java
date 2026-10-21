package growthbook.sdk.java.evaluators;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import java.util.List;
import java.util.Map;

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

    /**
     * Evaluate a batch of features against a single shared {@link EvaluationContext}.
     *
     * @param featureKeys    the feature keys to evaluate
     * @param context        the shared evaluation context, reused for every key
     * @param valueTypeClass the expected value type
     * @param <ValueType>    the type of value for the variations
     * @return a map from feature key to its feature result
     */
    <ValueType> Map<String, FeatureResult<ValueType>> evaluateFeatures(List<String> featureKeys, EvaluationContext context, Class<ValueType> valueTypeClass);
}
