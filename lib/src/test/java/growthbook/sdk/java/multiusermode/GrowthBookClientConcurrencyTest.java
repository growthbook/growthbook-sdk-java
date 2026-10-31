package growthbook.sdk.java.multiusermode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GrowthBookClientConcurrencyTest {

    private static final int THREADS = 8;
    private static final int EXPERIMENTS_PER_THREAD = 50;

    @Test
    @Timeout(30)
    @DisplayName("Concurrent runs and subscriptions on one client notify a subscriber once per experiment without failing")
    void concurrentRunsAndSubscriptionsNotifyOncePerExperiment() throws Exception {
        // Given
        GrowthBookClient client = new GrowthBookClient();
        CountingRunCallback subscriber = new CountingRunCallback();
        client.subscribe(subscriber);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            int threadId = t;
            startDaemon(() -> {
                for (int i = 0; i < EXPERIMENTS_PER_THREAD; i++) {
                    Experiment<String> experiment = experiment("exp-" + threadId + "-" + i);
                    client.run(experiment, user("user-" + threadId + "-" + i));
                    client.subscribe(new CountingRunCallback());
                }
            }, start, done, failures);
        }

        // When
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);

        // Then
        assertTrue(finished);
        assertEquals(Collections.<Throwable>emptyList(), failures);
        assertEquals(THREADS * EXPERIMENTS_PER_THREAD, subscriber.fires.size());
        for (AtomicInteger fires : subscriber.fires.values()) {
            assertEquals(1, fires.get());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Running one experiment for one user on many threads notifies the subscriber exactly once")
    void sameExperimentAcrossThreadsNotifiesOnce() throws Exception {
        // Given
        GrowthBookClient client = new GrowthBookClient();
        CountingRunCallback subscriber = new CountingRunCallback();
        client.subscribe(subscriber);
        Experiment<String> experiment = experiment("shared-exp");
        UserContext user = user("same-user");
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            startDaemon(() -> {
                for (int i = 0; i < EXPERIMENTS_PER_THREAD; i++) {
                    client.run(experiment, user);
                }
            }, start, done, failures);
        }

        // When
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);

        // Then
        assertTrue(finished);
        assertEquals(Collections.<Throwable>emptyList(), failures);
        assertEquals(Collections.singleton("shared-exp"), subscriber.fires.keySet());
        assertEquals(1, subscriber.fires.get("shared-exp").get());
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder()
                .key(key)
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
    }

    private static UserContext user(String id) {
        return UserContext.builder().attributesJson("{\"id\":\"" + id + "\"}").build();
    }

    private static void startDaemon(Runnable work, CountDownLatch start, CountDownLatch done, List<Throwable> failures) {
        Thread thread = new Thread(() -> {
            try {
                start.await();
                work.run();
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                done.countDown();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static final class CountingRunCallback implements ExperimentRunCallback {
        private final Map<String, AtomicInteger> fires = new ConcurrentHashMap<>();

        @Override
        public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> result) {
            fires.computeIfAbsent(experiment.getKey(), k -> new AtomicInteger()).incrementAndGet();
        }
    }
}
