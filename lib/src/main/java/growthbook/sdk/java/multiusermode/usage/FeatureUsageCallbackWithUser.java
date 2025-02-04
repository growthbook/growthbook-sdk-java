package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;

public interface FeatureUsageCallbackWithUser {

    <ValueType> void onFeatureUsage(String featureKey, FeatureResult<ValueType> result, UserContext userContext);
}
