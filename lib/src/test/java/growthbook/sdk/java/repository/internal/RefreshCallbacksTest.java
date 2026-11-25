package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class RefreshCallbacksTest {

    private static final String FEATURES = "{\"flag-a\":{\"defaultValue\":true}}";

    private final RefreshCallbacks callbacks = new RefreshCallbacks();

    @Test
    @DisplayName("Delivers refreshes and errors to every registered callback")
    void deliversToEveryCallback() {
        // Given
        RecordingRefreshCallback first = new RecordingRefreshCallback();
        RecordingRefreshCallback second = new RecordingRefreshCallback();
        callbacks.add(first);
        callbacks.add(second);
        IllegalStateException error = new IllegalStateException("synthetic failure");

        // When
        callbacks.notifyRefresh(FEATURES);
        callbacks.notifyError(error);

        // Then
        assertEquals(Collections.singletonList(FEATURES), first.refreshes);
        assertEquals(Collections.singletonList(FEATURES), second.refreshes);
        assertSame(error, first.errors.get(0));
        assertSame(error, second.errors.get(0));
    }

    @Test
    @DisplayName("Ignores a duplicate registration of the same callback")
    void ignoresDuplicateRegistration() {
        // Given
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        callbacks.add(callback);

        // When
        callbacks.add(callback);
        callbacks.notifyRefresh(FEATURES);

        // Then
        assertEquals(1, callback.refreshes.size());
    }

    @Test
    @DisplayName("Ignores a null callback")
    void ignoresNullCallback() {
        // Given
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        callbacks.add(callback);

        // When
        callbacks.add(null);

        // Then
        assertDoesNotThrow(() -> callbacks.notifyRefresh(FEATURES));
        assertDoesNotThrow(() -> callbacks.notifyError(new IllegalStateException("synthetic failure")));
        assertEquals(1, callback.refreshes.size());
    }

    @Test
    @DisplayName("Keeps notifying the other callbacks when one throws")
    void throwingCallbackDoesNotStopOthers() {
        // Given
        RecordingRefreshCallback before = new RecordingRefreshCallback();
        RecordingRefreshCallback after = new RecordingRefreshCallback();
        callbacks.add(before);
        callbacks.add(new ThrowingCallback());
        callbacks.add(after);

        // When
        callbacks.notifyRefresh(FEATURES);
        callbacks.notifyError(new IllegalStateException("synthetic failure"));

        // Then
        assertEquals(1, before.refreshes.size());
        assertEquals(1, after.refreshes.size());
        assertEquals(1, before.errors.size());
        assertEquals(1, after.errors.size());
    }

    @Test
    @DisplayName("Removes every callback on clear")
    void clearRemovesAllCallbacks() {
        // Given
        RecordingRefreshCallback first = new RecordingRefreshCallback();
        RecordingRefreshCallback second = new RecordingRefreshCallback();
        callbacks.add(first);
        callbacks.add(second);

        // When
        callbacks.clear();
        callbacks.notifyRefresh(FEATURES);
        callbacks.notifyError(new IllegalStateException("synthetic failure"));

        // Then
        assertTrue(first.refreshes.isEmpty());
        assertTrue(second.refreshes.isEmpty());
        assertTrue(first.errors.isEmpty());
        assertTrue(second.errors.isEmpty());
    }

    @Test
    @DisplayName("Accepts a registration from another thread while a dispatch is in progress")
    void acceptsRegistrationDuringDispatch() throws Exception {
        // Given
        CountDownLatch dispatching = new CountDownLatch(1);
        CountDownLatch registered = new CountDownLatch(1);
        RecordingRefreshCallback late = new RecordingRefreshCallback();
        callbacks.add(new BlockingCallback(dispatching, registered));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> registration = executor.submit(() -> {
                dispatching.await(5, TimeUnit.SECONDS);
                callbacks.add(late);
                registered.countDown();
                return null;
            });

            // When
            assertDoesNotThrow(() -> callbacks.notifyRefresh(FEATURES));
            registration.get(5, TimeUnit.SECONDS);
            callbacks.notifyRefresh(FEATURES);

            // Then
            assertEquals(Collections.singletonList(FEATURES), late.refreshes);
        } finally {
            executor.shutdownNow();
        }
    }


    private static final class ThrowingCallback implements growthbook.sdk.java.callback.FeatureRefreshCallback {
        @Override
        public void onRefresh(String featuresJson) {
            throw new IllegalStateException("synthetic callback failure");
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException("synthetic callback failure");
        }
    }

    private static final class BlockingCallback implements growthbook.sdk.java.callback.FeatureRefreshCallback {
        private final CountDownLatch dispatching;
        private final CountDownLatch registered;

        private BlockingCallback(CountDownLatch dispatching, CountDownLatch registered) {
            this.dispatching = dispatching;
            this.registered = registered;
        }

        @Override
        public void onRefresh(String featuresJson) {
            dispatching.countDown();
            try {
                registered.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void onError(Throwable throwable) {
        }
    }
}
