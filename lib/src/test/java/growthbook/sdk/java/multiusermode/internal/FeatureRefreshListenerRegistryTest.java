package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Timeout(30)
    void closingTheLastSubscriptionWhileAnotherSubscribesKeepsTheNewHandleLive() throws Exception {
        // Race: the last close() and a fresh subscribe() for the same listener run at once. The close
        // used to remove the listener outside the counted region, so it could drop a listener that the
        // new subscription had already re-registered — leaving the new handle open but silently
        // starved of events. Each round has one open subscription left after the dust settles, so the
        // listener must receive that round's publish; a single miss means the race reappeared.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            for (int round = 0; round < 5000; round++) {
                AtomicInteger fires = new AtomicInteger();
                FeatureRefreshListener listener = event -> fires.incrementAndGet();
                FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);

                FeatureRefreshSubscription first = registry.subscribe(listener);

                Future<FeatureRefreshSubscription> survivor = pool.submit(() -> {
                    barrier.await();
                    return registry.subscribe(listener);
                });
                Future<?> closer = pool.submit(() -> {
                    barrier.await();
                    first.close();
                    return null;
                });
                FeatureRefreshSubscription second = survivor.get();
                closer.get();

                registry.publish(sampleEvent());
                assertEquals(1, fires.get(), "surviving subscription missed an event on round " + round);
                second.close();
            }
        } finally {
            pool.shutdownNow();
        }
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
