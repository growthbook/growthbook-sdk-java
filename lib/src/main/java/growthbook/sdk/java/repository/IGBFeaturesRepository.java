package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.listener.FeatureRefreshListener;

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

    /**
     * Registers a legacy feature refresh callback.
     *
     * @param callback callback to register
     * @deprecated Use {@link #addFeatureRefreshListener(FeatureRefreshListener)}.
     */
    @Deprecated
    void onFeaturesRefresh(FeatureRefreshCallback callback);

    default void addFeatureRefreshListener(FeatureRefreshListener listener) {
        // Optional for repository implementations that do not refresh features.
    }

    default void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        // Optional for repository implementations that do not refresh features.
    }

    /**
     * Clears legacy feature refresh callbacks.
     *
     * @deprecated Use listener-specific unsubscription with
     * {@link #removeFeatureRefreshListener(FeatureRefreshListener)} where available.
     */
    @Deprecated
    void clearCallbacks();
}
