package growthbook.sdk.java.listener;

/**
 * Handle used to unsubscribe a feature refresh listener.
 */
@FunctionalInterface
public interface FeatureRefreshSubscription extends AutoCloseable {
    @Override
    void close();
}
