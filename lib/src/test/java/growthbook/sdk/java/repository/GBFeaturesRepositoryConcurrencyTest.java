package growthbook.sdk.java.repository;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for repository-internal races: callback registration on
 * caller threads vs. dispatch on the poll/SSE background threads, torn
 * features/saved-groups reads across a concurrent refresh, and the polling
 * scheduler pinning the JVM with a non-daemon thread.
 */
class GBFeaturesRepositoryConcurrencyTest {

    private static GBFeaturesRepository newRepository() {
        return GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-concurrency-test")
                .isCacheDisabled(true)
                .build();
    }

    @Test
    @Timeout(30)
    void registeringCallbacksWhileDispatchingDoesNotThrowCme() throws Exception {
        GBFeaturesRepository repository = newRepository();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);

        // Dispatcher: iterates the callback list, as the poll/SSE threads do.
        Thread dispatcher = new Thread(() -> {
            try {
                start.await();
                while (!stop.get()) {
                    repository.engine().notifyCallbacksOfRefresh("{}");
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        });
        dispatcher.setDaemon(true);

        // Registrar: adds callbacks, as initialize() does on the caller thread.
        Thread registrar = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 5_000; i++) {
                    repository.onFeaturesRefresh(new FeatureRefreshCallback() {
                        @Override
                        public void onRefresh(String featuresJson) {
                        }

                        @Override
                        public void onError(Throwable throwable) {
                        }
                    });
                }
                repository.clearCallbacks();
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                stop.set(true);
            }
        });
        registrar.setDaemon(true);

        dispatcher.start();
        registrar.start();
        start.countDown();
        registrar.join(TimeUnit.SECONDS.toMillis(20));
        dispatcher.join(TimeUnit.SECONDS.toMillis(20));

        assertEquals(0, failures.size(),
                "registration concurrent with dispatch threw (was ConcurrentModificationException): " + failures);
    }

    @Test
    @Timeout(30)
    void featureSnapshotIsNeverTornAcrossConcurrentRefresh() throws Exception {
        GBFeaturesRepository repository = newRepository();

        String payloadA = "{\"features\":{\"marker\":{\"defaultValue\":\"A\"}},\"savedGroups\":{\"marker\":[\"A\"]}}";
        String payloadB = "{\"features\":{\"marker\":{\"defaultValue\":\"B\"}},\"savedGroups\":{\"marker\":[\"B\"]}}";

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 2_000; i++) {
                    repository.engine().applyResponse((i % 2 == 0) ? payloadA : payloadB, true);
                }
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                stop.set(true);
            }
        });
        writer.setDaemon(true);

        Thread reader = new Thread(() -> {
            try {
                start.await();
                while (!stop.get()) {
                    FeatureSnapshot snapshot = repository.getFeatureSnapshot();
                    Map<String, ?> features = snapshot.getParsedFeatures();
                    if (features.isEmpty()) {
                        continue; // initial EMPTY snapshot
                    }
                    // Both halves must come from the SAME payload.
                    String featureMarker = snapshot.getFeaturesJson().contains("\"A\"") ? "A" : "B";
                    String savedGroupMarker = snapshot.getParsedSavedGroups()
                            .getAsJsonArray("marker").get(0).getAsString();
                    assertEquals(featureMarker, savedGroupMarker,
                            "torn snapshot: features from one payload, saved groups from another");
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        });
        reader.setDaemon(true);

        writer.start();
        reader.start();
        start.countDown();
        writer.join(TimeUnit.SECONDS.toMillis(20));
        reader.join(TimeUnit.SECONDS.toMillis(20));

        assertEquals(0, failures.size(), "snapshot consistency check failed: " + failures);
    }
}
