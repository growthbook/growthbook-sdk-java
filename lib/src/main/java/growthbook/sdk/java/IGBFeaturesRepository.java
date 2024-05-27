package growthbook.sdk.java;

/**
 * INTERNAL: Interface that is used internally for the {@link GBFeaturesRepository}
 */
interface IGBFeaturesRepository {
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
