package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FeatureRefreshListenerRegistryTest {

    private static FeatureRefreshEvent sampleEvent() {
        return FeatureRefreshEvent.success(
                true,
                false,
                1,
                FeatureRefreshSource.MANUAL,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                1
        );
    }

    @Test
    void publishDropsTheEventWhenTheExecutorRejectsInsteadOfRunningInline() {
        // Deliberately not a synchronous fallback: publish() runs on the polling or SSE refresh
        // thread, so running the listener there could stall feature updates for the whole client —
        // precisely what the executor exists to prevent. A caller whose executor rejects asked for
        // back-pressure, and a refresh event is a notification, not a transaction: the next
        // refresh carries the current state.
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        FeatureRefreshEvent event = sampleEvent();
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry(rejectingExecutor);

        registry.add(listener);

        // The rejection must not surface through the refresh path either.
        assertDoesNotThrow(() -> registry.publish(event));
        verify(listener, never()).onRefresh(any());
    }

    @Test
    void subscribeTwiceWithTheSameListenerGivesTwoWorkingHandles() {
        // A second subscriber used to get a no-op handle and could never unsubscribe. Counting
        // subscriptions keeps both handles usable without letting either one cut off the other.
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);

        FeatureRefreshSubscription first = registry.subscribe(listener);
        FeatureRefreshSubscription second = registry.subscribe(listener);

        // Subscribing twice must not register the listener twice: one event, one callback.
        registry.publish(sampleEvent());
        verify(listener, times(1)).onRefresh(any());

        first.close();
        registry.publish(sampleEvent());
        verify(listener, times(2)).onRefresh(any());   // still subscribed through the second handle

        second.close();
        registry.publish(sampleEvent());
        verify(listener, times(2)).onRefresh(any());   // last handle closed, no further events
    }

    @Test
    void closingASubscriptionTwiceDoesNotDropAnotherSubscription() {
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);

        FeatureRefreshSubscription first = registry.subscribe(listener);
        FeatureRefreshSubscription second = registry.subscribe(listener);

        first.close();
        first.close();

        registry.publish(sampleEvent());
        verify(listener, times(1)).onRefresh(any());

        second.close();
        registry.publish(sampleEvent());
        verify(listener, times(1)).onRefresh(any());
    }

    @Test
    void removeDropsTheListenerEvenWithOpenSubscriptions() {
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);

        registry.subscribe(listener);
        registry.subscribe(listener);
        registry.remove(listener);

        registry.publish(sampleEvent());
        verify(listener, never()).onRefresh(any());
    }

    @Test
    void publishIsolatesListenerErrorsAndStillNotifiesRemainingListeners() {
        FeatureRefreshListener throwingListener = mock(FeatureRefreshListener.class);
        FeatureRefreshListener healthyListener = mock(FeatureRefreshListener.class);
        doThrow(new AssertionError("listener blew up")).when(throwingListener).onRefresh(any());
        FeatureRefreshEvent event = sampleEvent();
        // null executor -> synchronous dispatch on the calling thread
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);

        registry.add(throwingListener);
        registry.add(healthyListener);

        assertDoesNotThrow(() -> registry.publish(event));
        verify(throwingListener).onRefresh(event);
        verify(healthyListener).onRefresh(event);
    }
}
