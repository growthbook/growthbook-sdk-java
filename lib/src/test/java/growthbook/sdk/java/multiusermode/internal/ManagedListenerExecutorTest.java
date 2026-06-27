package growthbook.sdk.java.multiusermode.internal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ManagedListenerExecutorTest {

    @Test
    void resolveUsesSuppliedExecutorAndDoesNotOwnItsLifecycle() {
        ExecutorService supplied = mock(ExecutorService.class);

        ManagedListenerExecutor managed = ManagedListenerExecutor.resolve(supplied);

        assertSame(supplied, managed.executor());
        managed.shutdown();
        verify(supplied, never()).shutdown();
    }

    @Test
    void resolveCreatesOwnedDaemonExecutorWhenNoneSupplied() throws InterruptedException {
        ManagedListenerExecutor managed = ManagedListenerExecutor.resolve(null);
        try {
            CountDownLatch ran = new CountDownLatch(1);
            AtomicBoolean daemon = new AtomicBoolean(false);
            managed.executor().execute(() -> {
                daemon.set(Thread.currentThread().isDaemon());
                ran.countDown();
            });

            assertTrue(ran.await(5, TimeUnit.SECONDS), "owned executor did not run the task");
            assertTrue(daemon.get(), "owned executor thread should be a daemon");
        } finally {
            managed.shutdown();
        }
    }

    @Test
    void shutdownStopsTheOwnedExecutor() {
        ManagedListenerExecutor managed = ManagedListenerExecutor.resolve(null);

        managed.shutdown();

        assertThrows(RejectedExecutionException.class, () -> managed.executor().execute(() -> { }));
    }
}
