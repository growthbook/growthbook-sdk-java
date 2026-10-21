package growthbook.sdk.java.callback;

import growthbook.sdk.java.model.FeatureResult;

/**
 * Listen for feature usage events
 */
public interface FeatureUsageCallback {
    <T> void onFeatureUsage(String featureKey, FeatureResult<T> result);
}
