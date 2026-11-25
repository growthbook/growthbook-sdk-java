package growthbook.sdk.java.featurefetch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.repository.RefreshMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

class FeatureRefreshSchedulerTest {

    private static final long AWAIT_SECONDS = 5;

    private final FeatureRefreshScheduler scheduler = new FeatureRefreshScheduler();

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @ParameterizedTest(name = "mode = {0}")
    @NullSource
    @EnumSource(value = RefreshMode.class, names = "DEFAULT")
    @DisplayName("Runs a default or unspecified refresh synchronously on the calling thread")
    void runsDefaultRefreshSynchronouslyOnCallingThread(RefreshMode mode) {
        // Given
        RecordingAction action = new RecordingAction();

        // When
        scheduler.requestRefresh(mode, action);

        // Then
        assertEquals(1, action.runs.size());
        assertSame(Thread.currentThread(), action.runs.get(0).thread);
        assertEquals(RefreshMode.DEFAULT, action.runs.get(0).mode);
    }

    @Test
    @DisplayName("Rejects a null refresh action")
    void rejectsNullRefreshAction() {
        // When
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> scheduler.requestRefresh(RefreshMode.DEFAULT, null));

        // Then
        assertEquals("refreshAction", thrown.getMessage());
    }

    @Test
    @DisplayName("Does not propagate a fetch failure from a synchronous refresh")
    void doesNotPropagateFetchFailureFromSynchronousRefresh() {
        // Given
        FeatureRefreshScheduler.FeatureRefreshAction failing = mode -> {
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, "offline");
        };

        // When / Then
        assertDoesNotThrow(() -> scheduler.requestRefresh(RefreshMode.DEFAULT, failing));
    }

    @Test
    @DisplayName("Runs a forced refresh on the daemon background refresh thread")
    void runsForcedRefreshOnBackgroundThread() throws InterruptedException {
        // Given
        BlockingQueue<Run> runs = new LinkedBlockingQueue<>();

        // When
        scheduler.requestRefresh(RefreshMode.FORCE, mode -> runs.add(new Run(Thread.currentThread(), mode)));
        Run run = runs.poll(AWAIT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertEquals(RefreshMode.FORCE, run.mode);
        assertEquals("growthbook-feature-refresh", run.thread.getName());
        assertTrue(run.thread.isDaemon());
    }

    @Test
    @DisplayName("Keeps the background refresh thread running after a forced refresh fails")
    void keepsBackgroundThreadAfterForcedRefreshFails() throws InterruptedException {
        // Given
        BlockingQueue<Thread> threads = new LinkedBlockingQueue<>();

        // When
        scheduler.requestRefresh(RefreshMode.FORCE, mode -> {
            threads.add(Thread.currentThread());
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR, "status 500");
        });
        scheduler.requestRefresh(RefreshMode.FORCE, mode -> {
            threads.add(Thread.currentThread());
            throw new IllegalStateException("unexpected failure");
        });
        scheduler.requestRefresh(RefreshMode.FORCE, mode -> threads.add(Thread.currentThread()));
        Thread first = threads.poll(AWAIT_SECONDS, TimeUnit.SECONDS);
        Thread second = threads.poll(AWAIT_SECONDS, TimeUnit.SECONDS);
        Thread third = threads.poll(AWAIT_SECONDS, TimeUnit.SECONDS);

        // Then
        assertSame(first, second);
        assertSame(first, third);
    }

    @Test
    @DisplayName("Skips a forced refresh requested after shutdown")
    void skipsForcedRefreshAfterShutdown() {
        // Given
        RecordingAction action = new RecordingAction();
        scheduler.shutdown();

        // When
        scheduler.requestRefresh(RefreshMode.FORCE, action);

        // Then
        assertTrue(action.runs.isEmpty());
    }

    private static final class Run {
        private final Thread thread;
        private final RefreshMode mode;

        private Run(Thread thread, RefreshMode mode) {
            this.thread = thread;
            this.mode = mode;
        }
    }

    private static final class RecordingAction implements FeatureRefreshScheduler.FeatureRefreshAction {
        private final List<Run> runs = new ArrayList<>();

        @Override
        public void refresh(RefreshMode refreshMode) {
            runs.add(new Run(Thread.currentThread(), refreshMode));
        }
    }
}
