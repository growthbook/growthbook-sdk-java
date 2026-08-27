package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
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
    void publishFallsBackToSynchronousDispatchWhenExecutorRejects() {
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        FeatureRefreshEvent event = sampleEvent();
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        FeatureRefreshListenerRegistry registry = new FeatureRefreshListenerRegistry(rejectingExecutor);

        registry.add(listener);

        assertDoesNotThrow(() -> registry.publish(event));
        verify(listener).onRefresh(event);
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
