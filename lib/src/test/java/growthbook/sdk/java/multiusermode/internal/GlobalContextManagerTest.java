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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("Verify: a concurrent refresh cannot interleave while initialize holds the publish lock, and the live snapshot wins")
    void initializeDoesNotClobberConcurrentRefresh() throws Exception {
        // Gate the first (initialize) snapshot read so initialize parks inside publishSnapshot while
        // still holding the publish lock; the refresh's read returns the live snapshot.
        AtomicInteger snapshotReads = new AtomicInteger(0);
        CountDownLatch initInsidePublish = new CountDownLatch(1);
        CountDownLatch releaseInit = new CountDownLatch(1);

        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getFeatureSnapshot()).thenAnswer(invocation -> {
            if (snapshotReads.incrementAndGet() == 1) {
                initInsidePublish.countDown();
                releaseInit.await();
                return SEED;
            }
            return LIVE;
        });

        GlobalContextManager manager = new GlobalContextManager(Options.builder().build());

        Thread initializer = new Thread(() -> manager.initialize(repository), "initializer");
        Thread refresher = new Thread(() -> manager.refresh(repository), "refresher");
        initializer.setDaemon(true);
        refresher.setDaemon(true);

        // initialize enters publishSnapshot, reads the seed, and parks while still holding the lock.
        initializer.start();
        initInsidePublish.await();

        // refresh cannot acquire the publish lock while initialize holds it: it blocks on the monitor
        // and never reaches its own snapshot read. Spin only until that state is observable; @Timeout
        // is the sole guard against a hang if the lock were missing.
        refresher.start();
        while (refresher.getState() != Thread.State.BLOCKED && refresher.isAlive()) {
            Thread.yield();
        }
        assertEquals(Thread.State.BLOCKED, refresher.getState(),
                "refresh entered publishSnapshot while initialize held the lock");
        assertEquals(1, snapshotReads.get(),
                "refresh read a snapshot while initialize held the lock");

        // Release initialize; the refresh then runs and the newer (live) snapshot must win.
        releaseInit.countDown();
        initializer.join();
        refresher.join();

        assertEquals(LIVE.getParsedFeatures().size(), manager.featureCount(),
                "initialize() clobbered the concurrently refreshed snapshot");
    }

    private static FeatureSnapshot snapshotWith(int featureCount) {
        Map<String, Feature<?>> features = new HashMap<>();
        for (int i = 0; i < featureCount; i++) {
            features.put("flag-" + i, new Feature<>());
        }
        return FeatureSnapshot.of("{}", "{}", features, new JsonObject());
    }
}
