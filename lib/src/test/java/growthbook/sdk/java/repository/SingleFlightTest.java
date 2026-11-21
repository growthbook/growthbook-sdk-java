package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.exception.FeatureFetchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for the single-flight de-duplication primitive. Concurrency is made deterministic by gating
 * the leader's action on a latch and waiting for follower threads to actually park on the leader's
 * result (via thread state) before releasing — no {@code Thread.sleep}, no timing assumptions.
 */
class SingleFlightTest {

    private static final String KEY = "key";

    @Test
    @DisplayName("Verify: a lone caller runs the action once and returns its result")
    void loneCallerRunsActionOnce() throws FeatureFetchException {
        // Given a single-flight and a counting action
        SingleFlight<String> singleFlight = new SingleFlight<>();
        AtomicInteger runs = new AtomicInteger();

        // When run once
        String result = singleFlight.run(KEY, () -> {
            runs.incrementAndGet();
            return "payload";
        });

        // Then the action ran exactly once and nothing remains in flight
        assertEquals("payload", result);
        assertEquals(1, runs.get());
        assertEquals(0, singleFlight.inFlightCount());
    }

    @Test
    @DisplayName("Verify: concurrent callers for the same key collapse to a single action run")
    void concurrentCallersCollapseToSingleRun() throws InterruptedException {
        // Given a leader parked inside its action while followers pile up on the same key
        SingleFlight<String> singleFlight = new SingleFlight<>();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch leaderInAction = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread leader = new Thread(() -> runQuietly(singleFlight, () -> {
            runs.incrementAndGet();
            leaderInAction.countDown();
            awaitQuietly(release);
            return "payload";
        }));
        leader.start();
        assertTrue(leaderInAction.await(5, TimeUnit.SECONDS));

        List<Thread> followers = new ArrayList<>();
        List<AtomicReference<String>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            AtomicReference<String> slot = new AtomicReference<>();
            results.add(slot);
            Thread follower = new Thread(() -> slot.set(runQuietly(singleFlight, () -> "should-not-run")));
            followers.add(follower);
            follower.start();
        }
        // Followers cannot become leaders while the slot is held; wait until each is parked on the leader.
        followers.forEach(SingleFlightTest::awaitParked);

        // When the leader completes
        release.countDown();
        leader.join(5000);
        for (Thread follower : followers) {
            follower.join(5000);
        }

        // Then the action ran exactly once and every follower observed the leader's result
        assertEquals(1, runs.get());
        results.forEach(slot -> assertEquals("payload", slot.get()));
        assertEquals(0, singleFlight.inFlightCount());
    }

    @Test
    @DisplayName("Verify: when the leader fails, followers fall back to running the action themselves")
    void followersFallBackWhenLeaderFails() throws InterruptedException {
        // Given a leader whose action throws while followers wait on it
        SingleFlight<String> singleFlight = new SingleFlight<>();
        AtomicInteger fallbackRuns = new AtomicInteger();
        CountDownLatch leaderInAction = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> leaderError = new AtomicReference<>();

        Thread leader = new Thread(() -> {
            try {
                singleFlight.run(KEY, () -> {
                    leaderInAction.countDown();
                    awaitQuietly(release);
                    throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN, "boom");
                });
            } catch (FeatureFetchException e) {
                leaderError.set(e);
            }
        });
        leader.start();
        assertTrue(leaderInAction.await(5, TimeUnit.SECONDS));

        List<Thread> followers = new ArrayList<>();
        List<AtomicReference<String>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AtomicReference<String> slot = new AtomicReference<>();
            results.add(slot);
            Thread follower = new Thread(() -> slot.set(runQuietly(singleFlight, () -> {
                fallbackRuns.incrementAndGet();
                return "fallback";
            })));
            followers.add(follower);
            follower.start();
        }
        followers.forEach(SingleFlightTest::awaitParked);

        // When the leader fails
        release.countDown();
        leader.join(5000);
        for (Thread follower : followers) {
            follower.join(5000);
        }

        // Then the leader surfaced its failure and each follower produced its own result
        assertInstanceOf(FeatureFetchException.class, leaderError.get());
        assertEquals(3, fallbackRuns.get());
        results.forEach(slot -> assertEquals("fallback", slot.get()));
        assertEquals(0, singleFlight.inFlightCount());
    }

    @Test
    @DisplayName("Verify: an Error from the leader releases followers instead of blocking them forever")
    void leaderErrorReleasesFollowers() throws InterruptedException {
        // Given a leader whose action throws an Error (not a FeatureFetchException/RuntimeException)
        SingleFlight<String> singleFlight = new SingleFlight<>();
        AtomicInteger fallbackRuns = new AtomicInteger();
        CountDownLatch leaderInAction = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> leaderError = new AtomicReference<>();

        Thread leader = new Thread(() -> {
            try {
                singleFlight.run(KEY, () -> {
                    leaderInAction.countDown();
                    awaitQuietly(release);
                    throw new AssertionError("boom");
                });
            } catch (Throwable t) {
                leaderError.set(t);
            }
        });
        leader.start();
        assertTrue(leaderInAction.await(5, TimeUnit.SECONDS));

        AtomicReference<String> followerResult = new AtomicReference<>();
        Thread follower = new Thread(() -> followerResult.set(runQuietly(singleFlight, () -> {
            fallbackRuns.incrementAndGet();
            return "fallback";
        })));
        follower.start();
        awaitParked(follower);

        // When the leader fails with an Error
        release.countDown();
        leader.join(5000);
        follower.join(5000);

        // Then the follower was released and produced its own result (no deadlock)
        assertInstanceOf(AssertionError.class, leaderError.get());
        assertEquals("fallback", followerResult.get());
        assertEquals(1, fallbackRuns.get());
        assertEquals(0, singleFlight.inFlightCount());
    }

    @Test
    @DisplayName("Verify: a failed run leaves no in-flight entry, so the next call runs cleanly")
    void failedRunLeavesNoResidualInFlight() {
        // Given a first run whose action throws
        SingleFlight<String> singleFlight = new SingleFlight<>();
        assertThrows(FeatureFetchException.class, () -> singleFlight.run(KEY, () -> {
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN, "boom");
        }));

        // Then the key is idle again
        assertEquals(0, singleFlight.inFlightCount());

        // When a second run for the same key succeeds
        String second = runQuietly(singleFlight, () -> "payload");

        // Then it produced a result
        assertEquals("payload", second);
    }

    @Test
    @DisplayName("Verify: distinct keys run independently")
    void distinctKeysRunIndependently() throws FeatureFetchException {
        // Given a single-flight and two keys
        SingleFlight<String> singleFlight = new SingleFlight<>();
        AtomicInteger runs = new AtomicInteger();

        // When each key is run
        singleFlight.run("a", () -> {
            runs.incrementAndGet();
            return "a";
        });
        singleFlight.run("b", () -> {
            runs.incrementAndGet();
            return "b";
        });

        // Then both actions ran
        assertEquals(2, runs.get());
    }

    private static String runQuietly(SingleFlight<String> singleFlight, SingleFlight.Action<String> action) {
        try {
            return singleFlight.run(KEY, action);
        } catch (FeatureFetchException e) {
            throw new RuntimeException(e);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void awaitParked(Thread thread) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadlineNanos) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("thread did not park on the leader in time");
    }
}
