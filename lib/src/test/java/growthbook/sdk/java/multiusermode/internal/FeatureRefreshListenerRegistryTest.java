package growthbook.sdk.java.multiusermode.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FeatureRefreshListenerRegistryTest {

    @Test
    @DisplayName("Drops the event instead of running the listener inline when the executor rejects it")
    void dropsEventWhenExecutorRejects() {
        // Given
        RecordingRefreshListener listener = new RecordingRefreshListener();
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry(rejectingExecutor);
        registry.add(listener);

        // When
        registry.publish(sampleEvent());

        // Then
        assertEquals(Collections.<FeatureRefreshEvent>emptyList(), listener.events);
    }

    @Test
    @DisplayName("Subscribing one listener twice delivers each event once and keeps it until both handles close")
    void subscribingTwiceGivesTwoWorkingHandles() {
        // Given
        RecordingRefreshListener listener = new RecordingRefreshListener();
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);
        FeatureRefreshSubscription first = registry.subscribe(listener);
        FeatureRefreshSubscription second = registry.subscribe(listener);

        // When
        registry.publish(sampleEvent());
        int afterBothOpen = listener.events.size();
        first.close();
        registry.publish(sampleEvent());
        int afterFirstClosed = listener.events.size();
        second.close();
        registry.publish(sampleEvent());
        int afterBothClosed = listener.events.size();

        // Then
        assertEquals(1, afterBothOpen);
        assertEquals(2, afterFirstClosed);
        assertEquals(2, afterBothClosed);
    }

    @Test
    @DisplayName("Closing a subscription twice does not drop another subscription of the same listener")
    void closingSubscriptionTwiceKeepsOtherSubscription() {
        // Given
        RecordingRefreshListener listener = new RecordingRefreshListener();
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);
        FeatureRefreshSubscription first = registry.subscribe(listener);
        FeatureRefreshSubscription second = registry.subscribe(listener);

        // When
        first.close();
        first.close();
        registry.publish(sampleEvent());
        int whileSecondOpen = listener.events.size();
        second.close();
        registry.publish(sampleEvent());

        // Then
        assertEquals(1, whileSecondOpen);
        assertEquals(1, listener.events.size());
    }

    @Test
    @DisplayName("Removing a listener drops it even while it has open subscriptions")
    void removeDropsListenerWithOpenSubscriptions() {
        // Given
        RecordingRefreshListener listener = new RecordingRefreshListener();
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);
        registry.subscribe(listener);
        registry.subscribe(listener);

        // When
        registry.remove(listener);
        registry.publish(sampleEvent());

        // Then
        assertEquals(Collections.<FeatureRefreshEvent>emptyList(), listener.events);
    }

    @Test
    @Timeout(30)
    @DisplayName("A new subscription racing the close of the last one still receives events")
    void newSubscriptionRacingLastCloseStillReceivesEvents() throws Exception {
        // Given
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Integer> missedRounds = new ArrayList<>();

        try {
            // When
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
                FeatureRefreshSubscription second = survivor.get(10, TimeUnit.SECONDS);
                closer.get(10, TimeUnit.SECONDS);
                registry.publish(sampleEvent());
                if (fires.get() != 1) {
                    missedRounds.add(round);
                }
                second.close();
            }
        } finally {
            pool.shutdownNow();
        }

        // Then
        assertEquals(Collections.<Integer>emptyList(), missedRounds);
    }

    @Test
    @DisplayName("A throwing listener does not stop the remaining listeners from being notified")
    void throwingListenerDoesNotStopRemainingListeners() {
        // Given
        ThrowingListener throwingListener = new ThrowingListener();
        RecordingRefreshListener healthyListener = new RecordingRefreshListener();
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry((Executor) null);
        registry.add(throwingListener);
        registry.add(healthyListener);
        FeatureRefreshEvent event = sampleEvent();

        // When
        registry.publish(event);

        // Then
        assertEquals(1, throwingListener.calls.get());
        assertEquals(Collections.singletonList(event), healthyListener.events);
    }

    private static FeatureRefreshEvent sampleEvent() {
        return FeatureRefreshEvent.success(
                true,
                false,
                1,
                FeatureRefreshSource.MANUAL,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                1);
    }

    private static final class ThrowingListener implements FeatureRefreshListener {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void onRefresh(FeatureRefreshEvent event) {
            calls.incrementAndGet();
            throw new AssertionError("listener blew up");
        }
    }
}
