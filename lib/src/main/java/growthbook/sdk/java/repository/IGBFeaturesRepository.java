package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;

/**
 * INTERNAL: Interface that is used internally for the {@link GBFeaturesRepository}
 */
public interface IGBFeaturesRepository {
    void initialize() throws FeatureFetchException;
    void initialize(Boolean retryOnFailure) throws FeatureFetchException;

    /**
     * Required implementation to get the featuresJson
     * @return featuresJson String
     */
    String getFeaturesJson();

    void onFeaturesRefresh(FeatureRefreshCallback callback);

    /**
     * Clears the feature refresh callbacks
     */
    void clearCallbacks();
}
