package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.multiusermode.configurations.UserContext;

// Use Wrapper adapter design pattern - to allow an existing interface to function as if it was a new interface
// to maintain 100% backward compatibility.
public class FeatureUsageCallbackAdapter implements FeatureUsageCallbackWithUser {
    private final FeatureUsageCallback featureUsageCallback;

    // Constructor takes the old callback
    public FeatureUsageCallbackAdapter(FeatureUsageCallback featureUsageCallback) {
        this.featureUsageCallback = featureUsageCallback;
    }

    @Override
    public <ValueType> void onFeatureUsage(String featureKey, FeatureResult<ValueType> result, UserContext userContext) {
        // Delegate call to the old callback, ignoring the new userContext parameter
        if (featureUsageCallback != null) {
            featureUsageCallback.onFeatureUsage(featureKey, result);
        }
    }
}