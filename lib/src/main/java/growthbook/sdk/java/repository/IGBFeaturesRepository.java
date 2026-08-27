package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.listener.FeatureRefreshListener;

import javax.annotation.Nullable;

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

    /**
     * Returns the most recent feature refresh error, if any. Used for diagnostics only.
     *
     * @return the last refresh {@link Throwable}, or {@code null} when no refresh has failed
     */
    @Nullable
    default Throwable getLastRefreshError() {
        return null;
    }

    /**
     * Returns the epoch-millis timestamp of the most recent feature refresh error. Used for
     * diagnostics only.
     *
     * @return the timestamp in milliseconds, or {@code 0} when no refresh has failed
     */
    default long getLastRefreshErrorAtMillis() {
        return 0L;
    }

    default long getRefreshSuccessCount() {
        return 0L;
    }

    default long getRefreshFailureCount() {
        return 0L;
    }

    default long getRefreshConsecutiveFailureCount() {
        return 0L;
    }

    default long getLastRefreshFailureAtMillis() {
        return 0L;
    }

    @Nullable
    default Boolean getLastRefreshLoadedFromCache() {
        return null;
    }
}
