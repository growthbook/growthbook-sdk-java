package growthbook.sdk.java.multiusermode.internal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagedListenerExecutorTest {

    @Test
    @DisplayName("Uses a supplied executor and leaves its shutdown to the caller")
    void usesSuppliedExecutorWithoutOwningIt() {
        // Given
        ExecutorService supplied = mock(ExecutorService.class);
        ManagedListenerExecutor managed = ManagedListenerExecutor.resolve(supplied);

        // When
        managed.shutdown();

        // Then
        assertSame(supplied, managed.executor());
        verify(supplied, never()).shutdown();
    }

    @Test
    @DisplayName("Creates an owned executor running tasks on daemon threads when none is supplied")
    void createsOwnedDaemonExecutorWhenNoneSupplied() throws InterruptedException {
        // Given
        ManagedListenerExecutor managed = ManagedListenerExecutor.resolve(null);
        CountDownLatch ran = new CountDownLatch(1);
        AtomicBoolean daemon = new AtomicBoolean(false);

        try {
            // When
            managed.executor().execute(() -> {
                daemon.set(Thread.currentThread().isDaemon());
                ran.countDown();
            });

            // Then
            assertTrue(ran.await(5, TimeUnit.SECONDS));
            assertTrue(daemon.get());
        } finally {
            managed.shutdown();
        }
    }

    @Test
    @DisplayName("Shutting down stops the owned executor from accepting tasks")
    void shutdownStopsOwnedExecutor() {
        // Given
        ManagedListenerExecutor managed = ManagedListenerExecutor.resolve(null);

        // When
        managed.shutdown();

        // Then
        assertThrows(RejectedExecutionException.class, () -> managed.executor().execute(() -> { }));
    }
}
