package growthbook.sdk.java;

import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

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
    <ValueType> FeatureResult<ValueType> evaluateFeature(String key, GBContext context, Class<ValueType> valueTypeClass, JsonObject attributeOverrides, Map<String, JsonElement> forcedFeatures);
}
