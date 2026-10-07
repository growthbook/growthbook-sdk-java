package growthbook.sdk.java.listener;

import growthbook.sdk.java.model.FeatureRefreshEvent;

/**
 * Listener for metadata-only feature refresh events exposed by the client-level API.
 *
 * <p>Client-level listeners are dispatched off the refresh thread: on a dedicated daemon thread by
 * default, or on {@code Options.featureRefreshListenerExecutor} when supplied. Listeners added
 * directly on a repository always run synchronously on the refresh thread. Anything a listener
 * throws — exceptions and errors alike — is logged and isolated, and never fails the SDK refresh
 * operation.
 */
@FunctionalInterface
public interface FeatureRefreshListener {
    void onRefresh(FeatureRefreshEvent event);
}
