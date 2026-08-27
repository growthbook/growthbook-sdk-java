package growthbook.sdk.java.callback;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.repository.GBFeaturesRepository;

/**
 * Legacy callback invoked after feature refresh attempts.
 *
 * @deprecated Use {@link FeatureRefreshListener} with
 * {@link GrowthBookClient#addFeatureRefreshListener(FeatureRefreshListener)},
 * {@link GrowthBookClient#subscribeFeatureRefreshListener(FeatureRefreshListener)}, or
 * {@link GBFeaturesRepository#addFeatureRefreshListener(FeatureRefreshListener)}.
 */
@Deprecated
public interface FeatureRefreshCallback {

    /**
     * Invoked when features refresh successfully.
     *
     * @param featuresJson Features as JSON string
     * @deprecated Use {@link FeatureRefreshListener#onRefresh(FeatureRefreshEvent)}.
     */
    @Deprecated
    void onRefresh(String featuresJson);

    /**
     * Invoked when features fail to refresh.
     *
     * @param throwable Exception on refreshCallback
     * @deprecated Use {@link FeatureRefreshListener#onRefresh(FeatureRefreshEvent)}.
     */
    @Deprecated
    void onError(Throwable throwable);
}
