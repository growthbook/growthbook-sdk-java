package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.listener.internal.FeatureRefreshListenerDispatcher;
import growthbook.sdk.java.model.FeatureRefreshEvent;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;

/**
 * Internal listener registry for client-level feature refresh events.
 * Handles duplicate registration, optional executor dispatch, and listener failure isolation.
 */
public final class FeatureRefreshListenerRegistry {

    @Nullable
    private final Executor executor;
    private final FeatureRefreshListenerDispatcher dispatcher = new FeatureRefreshListenerDispatcher();

    /**
     * Creates a registry that dispatches through the given executor.
     *
     * @param executor executor for listener callbacks, or null for synchronous dispatch
     */
    public FeatureRefreshListenerRegistry(@Nullable Executor executor) {
        this.executor = executor;
    }

    /**
     * Adds a listener if it is non-null and not already registered.
     *
     * @param listener listener to register
     */
    public void add(FeatureRefreshListener listener) {
        dispatcher.add(listener);
    }

    /**
     * Adds a listener and returns a handle that removes it.
     *
     * @param listener listener to register
     * @return idempotent subscription handle
     */
    public FeatureRefreshSubscription subscribe(FeatureRefreshListener listener) {
        return dispatcher.subscribe(listener);
    }

    /**
     * Removes a registered listener.
     *
     * @param listener listener to remove
     */
    public void remove(FeatureRefreshListener listener) {
        dispatcher.remove(listener);
    }

    /**
     * Publishes an event to all registered listeners.
     *
     * @param event refresh event to publish
     */
    public void publish(FeatureRefreshEvent event) {
        dispatcher.publish(event, executor);
    }
}
