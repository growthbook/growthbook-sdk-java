package growthbook.sdk.java.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GBFeaturesRepositoryConcurrencyTest {

    private static final String PAYLOAD_A = "{\"features\":{\"marker\":{\"defaultValue\":\"A\"}},\"savedGroups\":{\"marker\":[\"A\"]}}";
    private static final String PAYLOAD_B = "{\"features\":{\"marker\":{\"defaultValue\":\"B\"}},\"savedGroups\":{\"marker\":[\"B\"]}}";

    @Test
    @Timeout(30)
    @DisplayName("Registering callbacks while refreshes notify them never throws")
    @SuppressWarnings("deprecation")
    void registeringCallbacksWhileNotifyingNeverThrows() throws Exception {
        // Given
        GBFeaturesRepository repository = newRepository();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread notifier = daemon(() -> {
            try {
                start.await();
                while (!stop.get()) {
                    repository.engine().notifyCallbacksOfRefresh("{}");
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        });
        Thread registrar = daemon(() -> {
            try {
                start.await();
                for (int i = 0; i < 5_000; i++) {
                    repository.onFeaturesRefresh(new NoOpRefreshCallback());
                }
                repository.clearCallbacks();
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                stop.set(true);
            }
        });

        // When
        notifier.start();
        registrar.start();
        start.countDown();
        registrar.join(TimeUnit.SECONDS.toMillis(20));
        notifier.join(TimeUnit.SECONDS.toMillis(20));

        // Then
        assertFalse(registrar.isAlive());
        assertFalse(notifier.isAlive());
        assertEquals(0, failures.size(), "registration concurrent with notification threw: " + failures);
    }

    @Test
    @Timeout(30)
    @DisplayName("A reader never sees features from one payload paired with saved groups from another")
    void readerNeverSeesTornSnapshot() throws Exception {
        // Given
        GBFeaturesRepository repository = newRepository();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread writer = daemon(() -> {
            try {
                start.await();
                for (int i = 0; i < 2_000; i++) {
                    repository.engine().applyResponse(i % 2 == 0 ? PAYLOAD_A : PAYLOAD_B, true);
                }
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                stop.set(true);
            }
        });
        Thread reader = daemon(() -> {
            try {
                start.await();
                while (!stop.get()) {
                    FeatureSnapshot snapshot = repository.getFeatureSnapshot();
                    if (snapshot.getParsedFeatures().isEmpty()) {
                        continue;
                    }
                    String featureMarker = snapshot.getFeaturesJson().contains("\"A\"") ? "A" : "B";
                    String savedGroupMarker = snapshot.getParsedSavedGroups()
                            .getAsJsonArray("marker").get(0).getAsString();
                    assertEquals(featureMarker, savedGroupMarker, "torn snapshot");
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        });

        // When
        writer.start();
        reader.start();
        start.countDown();
        writer.join(TimeUnit.SECONDS.toMillis(20));
        reader.join(TimeUnit.SECONDS.toMillis(20));

        // Then
        assertFalse(writer.isAlive());
        assertFalse(reader.isAlive());
        assertEquals(0, failures.size(), "snapshot consistency check failed: " + failures);
        assertTrue(repository.getFeaturesJson().contains("\"B\""));
    }

    private static GBFeaturesRepository newRepository() {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-concurrency-test")
                .isCacheDisabled(true)
                .build();
    }

    private static Thread daemon(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }

    @SuppressWarnings("deprecation")
    private static final class NoOpRefreshCallback implements growthbook.sdk.java.callback.FeatureRefreshCallback {
        @Override
        public void onRefresh(String featuresJson) {
        }

        @Override
        public void onError(Throwable throwable) {
        }
    }
}
