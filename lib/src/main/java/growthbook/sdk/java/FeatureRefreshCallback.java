package growthbook.sdk.java;

/**
 * See {@link GBFeaturesRepository#onFeaturesRefresh(FeatureRefreshCallback)}
 */
public interface FeatureRefreshCallback {

    /**
     * See {@link GBFeaturesRepository#onFeaturesRefresh(FeatureRefreshCallback)}
     *
     * @param featuresJson Features as JSON string
     */
    void onRefresh(String featuresJson);

    /**
     * See {@link GBFeaturesRepository#onFeaturesRefresh(FeatureRefreshCallback)}
     *
     * @param throwable Exception on refreshCallback
     */
    void onError(Throwable throwable);
}
