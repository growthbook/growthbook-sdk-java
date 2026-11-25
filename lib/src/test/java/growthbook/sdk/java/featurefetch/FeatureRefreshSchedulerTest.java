package growthbook.sdk.java.featurefetch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.repository.RefreshMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for the scheduler's two orthogonal axes: execution mode (now vs background) chosen by the
 * method, and cache semantics ({@link RefreshMode}) passed straight through to the action.
 */
class FeatureRefreshSchedulerTest {

    @Test
    @DisplayName("Verify: runNow executes on the calling thread and passes the refresh mode through")
    void runNowExecutesInlineWithMode() {
        // Given a scheduler and a recording action
        FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();
        AtomicReference<String> actionThread = new AtomicReference<>();
        AtomicReference<RefreshMode> observedMode = new AtomicReference<>();

        // When run now with FORCE semantics
        scheduler.runNow(RefreshMode.FORCE, mode -> {
            actionThread.set(Thread.currentThread().getName());
            observedMode.set(mode);
        });

        // Then it ran synchronously on the caller and saw FORCE
        assertEquals(Thread.currentThread().getName(), actionThread.get());
        assertEquals(RefreshMode.FORCE, observedMode.get());
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Verify: runNow logs and swallows a FeatureFetchException instead of propagating it")
    void runNowSwallowsFetchException() {
        // Given a scheduler and an action that fails
        FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();

        // When & Then the failure does not escape the scheduler
        assertDoesNotThrow(() -> scheduler.runNow(RefreshMode.DEFAULT, mode -> {
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN, "boom");
        }));
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Verify: runInBackground executes off the calling thread and passes the refresh mode through")
    void runInBackgroundExecutesOffThreadWithMode() throws InterruptedException {
        // Given a scheduler and a recording action
        FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> actionThread = new AtomicReference<>();
        AtomicReference<RefreshMode> observedMode = new AtomicReference<>();

        // When submitted to the background with FORCE semantics
        scheduler.runInBackground(RefreshMode.FORCE, mode -> {
            actionThread.set(Thread.currentThread().getName());
            observedMode.set(mode);
            ran.countDown();
        });

        // Then it runs on another thread and saw FORCE
        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread().getName(), actionThread.get());
        assertEquals(RefreshMode.FORCE, observedMode.get());
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Verify: the runInBackground convenience runs off-thread with DEFAULT semantics")
    void runInBackgroundConvenienceUsesDefaultMode() throws InterruptedException {
        // Given a scheduler and a recording action
        FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> actionThread = new AtomicReference<>();
        AtomicReference<RefreshMode> observedMode = new AtomicReference<>();

        // When submitted without a mode
        scheduler.runInBackground(mode -> {
            actionThread.set(Thread.currentThread().getName());
            observedMode.set(mode);
            ran.countDown();
        });

        // Then it runs off-thread with DEFAULT semantics
        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread().getName(), actionThread.get());
        assertEquals(RefreshMode.DEFAULT, observedMode.get());
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Verify: runNow rejects a null action")
    void runNowRejectsNullAction() {
        // Given a scheduler
        FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();

        // When & Then
        assertThrows(NullPointerException.class, () -> scheduler.runNow(RefreshMode.DEFAULT, null));
        scheduler.shutdown();
    }

    @Test
    @DisplayName("Verify: runInBackground rejects a null action")
    void runInBackgroundRejectsNullAction() {
        // Given a scheduler
        FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();

        // When & Then
        assertThrows(NullPointerException.class, () -> scheduler.runInBackground(null));
        scheduler.shutdown();
    }
}
