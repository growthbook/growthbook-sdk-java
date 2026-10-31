package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;

public interface FeatureUsageCallbackWithUser {

    <T> void onFeatureUsage(String featureKey, FeatureResult<T> result, UserContext userContext);
}
