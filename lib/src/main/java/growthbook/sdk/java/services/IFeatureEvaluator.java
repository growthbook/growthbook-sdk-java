package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.FeatureResult;

public interface IFeatureEvaluator {
    FeatureResult evaluateFeature(String key, Context context);
//    <ValueType>FeatureResult<ValueType> evaluateFeature(String key, Context context);
}
