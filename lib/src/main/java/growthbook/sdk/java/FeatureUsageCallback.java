package growthbook.sdk.java;

/**
 * Listen for feature usage events
 */
public interface FeatureUsageCallback {
    <ValueType> void onFeatureUsage(String featureKey, FeatureResult<ValueType> result);
}
