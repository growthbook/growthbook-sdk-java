package growthbook.sdk.java.listener.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Internal dispatcher for feature refresh listeners.
 * Listener failures are isolated from the SDK refresh flow: a failing listener is logged and
 * remaining listeners are still notified. Executor rejection falls back to synchronous dispatch
 * instead of surfacing through repository or client refresh APIs.
 */
@Slf4j
public final class FeatureRefreshListenerDispatcher {

    private final CopyOnWriteArrayList<FeatureRefreshListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener if it is non-null and not already registered.
     *
     * @param listener listener to register
     */
    public void add(FeatureRefreshListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    /**
     * Adds a listener and returns an idempotent subscription handle that removes it.
     *
     * @param listener listener to register
     * @return subscription handle
     */
    public FeatureRefreshSubscription subscribe(FeatureRefreshListener listener) {
        if (listener == null || !listeners.addIfAbsent(listener)) {
            return () -> {
            };
        }
        return () -> listeners.remove(listener);
    }

    /**
     * Removes a registered listener.
     *
     * @param listener listener to remove
     */
    public void remove(FeatureRefreshListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Removes all registered listeners.
     */
    public void clear() {
        listeners.clear();
    }

    /**
     * @return true when no listeners are registered
     */
    public boolean hasNoListeners() {
        return listeners.isEmpty();
    }

    /**
     * Publishes an event synchronously.
     *
     * @param event refresh event to publish
     */
    public void publish(FeatureRefreshEvent event) {
        publish(event, null);
    }

    /**
     * Publishes an event using the supplied executor when present, otherwise synchronously.
     *
     * @param event refresh event to publish
     * @param executor optional executor for listener callbacks
     */
    public void publish(FeatureRefreshEvent event, @Nullable Executor executor) {
        for (FeatureRefreshListener listener : listeners) {
            Runnable notification = () -> notifyListener(listener, event);
            if (executor == null) {
                notification.run();
                continue;
            }
            scheduleNotification(executor, listener, event, notification);
        }
    }

    private void scheduleNotification(
            Executor executor,
            FeatureRefreshListener listener,
            FeatureRefreshEvent event,
            Runnable notification
    ) {
        try {
            executor.execute(notification);
        } catch (RejectedExecutionException e) {
            log.warn(
                    "Feature refresh listener executor rejected listener={}; running synchronously. source={}, successful={}, featuresChanged={}",
                    listenerName(listener),
                    event.getSource(),
                    event.isSuccessful(),
                    event.isFeaturesChanged(),
                    e
            );
            notification.run();
        } catch (RuntimeException e) {
            log.warn(
                    "Unable to schedule feature refresh listener={}; running synchronously. source={}, successful={}, featuresChanged={}",
                    listenerName(listener),
                    event.getSource(),
                    event.isSuccessful(),
                    event.isFeaturesChanged(),
                    e
            );
            notification.run();
        }
    }

    private void notifyListener(FeatureRefreshListener listener, FeatureRefreshEvent event) {
        try {
            listener.onRefresh(event);
        } catch (Throwable e) {
            log.warn(
                    "Feature refresh listener {} failed while handling source={}, successful={}, featuresChanged={}",
                    listenerName(listener),
                    event.getSource(),
                    event.isSuccessful(),
                    event.isFeaturesChanged(),
                    e
            );
        }
    }

    private String listenerName(FeatureRefreshListener listener) {
        return listener == null ? "<null>" : listener.getClass().getName();
    }
}
