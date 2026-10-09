package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link GlobalContextManager} coordinates its publish paths: a seeded cold start
 * must not overwrite a newer snapshot already published by a concurrent refresh.
 */
class GlobalContextManagerTest {

    private static final FeatureSnapshot SEED = snapshotWith(1);
    private static final FeatureSnapshot LIVE = snapshotWith(2);

    @Test
    @Timeout(30)
    @DisplayName("Verify: a seeded initialize racing a live refresh converges to the live snapshot")
    void initializeDoesNotClobberConcurrentRefresh() throws Exception {
        // Given a repository where the first (initialize) snapshot read is held open until the
        // refresh has published the live snapshot — the exact losing interleave the lock must prevent.
        AtomicInteger snapshotReads = new AtomicInteger(0);
        CountDownLatch initializeEnteredRead = new CountDownLatch(1);
        CountDownLatch refreshPublished = new CountDownLatch(1);

        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getFeatureSnapshot()).thenAnswer(invocation -> {
            if (snapshotReads.incrementAndGet() == 1) {
                initializeEnteredRead.countDown();
                // Let the refresh fully publish the live snapshot before initialize finishes its own
                // publish. Under the fix the refresh is blocked on the publish lock and never arrives,
                // so this times out and initialize completes first — the refresh then wins.
                refreshPublished.await(2, TimeUnit.SECONDS);
                return SEED;
            }
            return LIVE;
        });

        GlobalContextManager manager = new GlobalContextManager(Options.builder().build());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Thread initializer = new Thread(() -> {
            await(start);
            manager.initialize(repository);
            done.countDown();
        });
        Thread refresher = new Thread(() -> {
            await(start);
            await(initializeEnteredRead);
            manager.refresh(repository);
            refreshPublished.countDown();
            done.countDown();
        });
        initializer.setDaemon(true);
        refresher.setDaemon(true);
        initializer.start();
        refresher.start();

        // When both publish paths run against the same repository.
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "publishers did not finish");

        // Then the live snapshot wins; initialize never leaves the context on the stale seed.
        assertEquals(LIVE.getParsedFeatures().size(), manager.featureCount(),
                "initialize() clobbered the concurrently refreshed snapshot");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static FeatureSnapshot snapshotWith(int featureCount) {
        Map<String, Feature<?>> features = new HashMap<>();
        for (int i = 0; i < featureCount; i++) {
            features.put("flag-" + i, new Feature<>());
        }
        return FeatureSnapshot.of("{}", "{}", features, new JsonObject());
    }
}
