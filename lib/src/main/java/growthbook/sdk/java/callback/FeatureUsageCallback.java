package growthbook.sdk.java.callback;

import growthbook.sdk.java.model.FeatureResult;

/**
 * Listen for feature usage events
 */
public interface FeatureUsageCallback {
    <ValueType> void onFeatureUsage(String featureKey, FeatureResult<ValueType> result);
}
