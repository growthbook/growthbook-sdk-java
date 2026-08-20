package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for shared-state races on the multi-user client: run() and
 * subscribe() are documented as safe on one shared instance, so the assigned
 * map and callback list must tolerate concurrent use.
 */
class GrowthBookClientConcurrencyTest {

    private static final int THREADS = 8;
    private static final int EXPERIMENTS_PER_THREAD = 50;

    @Test
    @Timeout(30)
    void concurrentRunAndSubscribeDoesNotCorruptSharedState() throws Exception {
        GrowthBookClient client = new GrowthBookClient();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicInteger subscriptionFires = new AtomicInteger(0);
        client.subscribe(new ExperimentRunCallback() {
            @Override
            public <ValueType> void onRun(Experiment<ValueType> experiment,
                                          growthbook.sdk.java.model.ExperimentResult<ValueType> result) {
                subscriptionFires.incrementAndGet();
            }
        });

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < EXPERIMENTS_PER_THREAD; i++) {
                        Experiment<String> experiment = Experiment.<String>builder()
                                .key("exp-" + threadId + "-" + i)
                                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                                .build();
                        UserContext user = UserContext.builder()
                                .attributesJson("{\"id\":\"user-" + threadId + "-" + i + "\"}")
                                .build();
                        client.run(experiment, user);
                        // Concurrent registration while other threads dispatch:
                        // previously an unsynchronized ArrayList add.
                        client.subscribe(new ExperimentRunCallback() {
                            @Override
                            public <ValueType> void onRun(Experiment<ValueType> e,
                                                          growthbook.sdk.java.model.ExperimentResult<ValueType> r) {
                                // no-op
                            }
                        });
                    }
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "workers did not finish");
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertEquals(0, failures.size(), "concurrent run()/subscribe() threw: " + failures);
        // Every distinct experiment key fired the pre-registered subscription
        // exactly once (each key is assigned once, and the change-check +
        // publish is atomic, so no double fire).
        assertEquals(THREADS * EXPERIMENTS_PER_THREAD, subscriptionFires.get());
    }

    @Test
    @Timeout(30)
    void sameExperimentAcrossThreadsFiresSubscriptionOnce() throws Exception {
        GrowthBookClient client = new GrowthBookClient();

        Map<String, AtomicInteger> firesByKey = new ConcurrentHashMap<>();
        client.subscribe(new ExperimentRunCallback() {
            @Override
            public <ValueType> void onRun(Experiment<ValueType> experiment,
                                          growthbook.sdk.java.model.ExperimentResult<ValueType> result) {
                firesByKey.computeIfAbsent(experiment.getKey(), k -> new AtomicInteger()).incrementAndGet();
            }
        });

        Experiment<String> experiment = Experiment.<String>builder()
                .key("shared-exp")
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
        // Same user everywhere: the assigned variation never changes, so the
        // atomic change-check must collapse all fires into exactly one.
        UserContext user = UserContext.builder()
                .attributesJson("{\"id\":\"same-user\"}")
                .build();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < EXPERIMENTS_PER_THREAD; i++) {
                        client.run(experiment, user);
                    }
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "workers did not finish");
        assertEquals(0, failures.size(), "concurrent run() threw: " + failures);
        assertEquals(1, firesByKey.get("shared-exp").get(),
                "unchanged assignment must fire the subscription exactly once");
    }
}
