package growthbook.sdk.java.listener.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>INTERNAL</b>: not part of the supported API. Public only because the client and repository packages
 * both dispatch through it — treat its signatures as free to change without a major version bump.
 *
 * Internal dispatcher for feature refresh listeners.
 * Listener failures are isolated from the SDK refresh flow: a failing listener is logged and
 * remaining listeners are still notified. Neither a failing nor an unschedulable listener
 * surfaces through repository or client refresh APIs, and neither ever runs on the refresh
 * thread when an executor is supplied — see {@link #scheduleNotification}.
 */
@Slf4j
public final class FeatureRefreshListenerDispatcher {

    private final CopyOnWriteArrayList<FeatureRefreshListener> listeners = new CopyOnWriteArrayList<>();

    /** Open subscription count per listener; absent means the listener holds no subscriptions. */
    private final ConcurrentMap<FeatureRefreshListener, AtomicInteger> subscriptionCounts = new ConcurrentHashMap<>();

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
     * <p>Subscriptions are counted per listener, so two callers sharing one listener instance each
     * get a handle that works: the listener stays registered until the last handle is closed.
     * Returning a no-op handle for the second subscriber instead would leave that caller unable to
     * unsubscribe at all, while removing on the first close would silently cut off the other one.
     * Closing a handle more than once is a no-op.
     *
     * @param listener listener to register
     * @return subscription handle
     */
    public FeatureRefreshSubscription subscribe(FeatureRefreshListener listener) {
        if (listener == null) {
            return () -> {
            };
        }

        subscriptionCounts.computeIfAbsent(listener, key -> new AtomicInteger()).incrementAndGet();
        listeners.addIfAbsent(listener);

        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                releaseSubscription(listener);
            }
        };
    }

    /**
     * Drops one subscription, removing the listener once none are left. The count is mutated inside
     * {@code compute} so a concurrent {@link #subscribe} cannot slip between the decrement and the
     * removal and lose its registration.
     */
    private void releaseSubscription(FeatureRefreshListener listener) {
        boolean[] lastSubscription = {false};
        subscriptionCounts.compute(listener, (key, count) -> {
            if (count == null || count.decrementAndGet() <= 0) {
                lastSubscription[0] = true;
                return null;
            }
            return count;
        });

        if (lastSubscription[0]) {
            listeners.remove(listener);
        }
    }

    /**
     * Removes a registered listener, regardless of how many subscriptions it holds.
     *
     * @param listener listener to remove
     */
    public void remove(FeatureRefreshListener listener) {
        if (listener != null) {
            subscriptionCounts.remove(listener);
            listeners.remove(listener);
        }
    }

    /**
     * Removes all registered listeners.
     */
    public void clear() {
        subscriptionCounts.clear();
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

    /**
     * Hands the notification to {@code executor}, dropping the event when it cannot be scheduled.
     *
     * <p>Running the listener here instead would put it on the caller's thread — the polling or
     * SSE refresh thread — which is exactly what the executor exists to prevent, so one slow
     * listener could stall feature updates for the whole client. Dropping is the safer trade:
     * a refresh event is a notification, not a transaction, and the next refresh carries the
     * current state. A caller whose executor rejects asked for back-pressure, and the default
     * owned executor has an unbounded queue, so it only rejects after shutdown — when there is
     * nothing left to deliver to anyway.
     */
    private void scheduleNotification(
            Executor executor,
            FeatureRefreshListener listener,
            FeatureRefreshEvent event,
            Runnable notification
    ) {
        try {
            executor.execute(notification);
        } catch (RuntimeException e) {
            // Covers RejectedExecutionException, which is itself a RuntimeException.
            log.warn(
                    "Feature refresh listener executor rejected listener={}; dropping the event. source={}, successful={}, featuresChanged={}",
                    listenerName(listener),
                    event.getSource(),
                    event.isSuccessful(),
                    event.isFeaturesChanged(),
                    e
            );
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
