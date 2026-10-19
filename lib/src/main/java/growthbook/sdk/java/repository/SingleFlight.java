package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs at most one {@code action} per key at a time and shares its result with all concurrent callers.
 *
 * <p>The first caller for an idle key becomes the <em>leader</em>: it runs the action, publishes the
 * result to any waiters, and returns it. Concurrent callers for the same key become <em>followers</em>:
 * they block on the leader's result instead of launching their own action (thundering-herd protection).
 * If the leader fails, its followers fall back to running the action themselves, so a single failure
 * never denies a result to everyone.
 *
 * <p>This is a general de-duplication primitive: it holds no cache and no domain knowledge. Freshness
 * and storage are the caller's concern. All operations are safe for concurrent use.
 *
 * @param <T> the action's result type
 */
final class SingleFlight<T> {

    /**
     * The work de-duplicated per key. May perform I/O and may throw.
     */
    @FunctionalInterface
    interface Action<T> {
        T run() throws FeatureFetchException;
    }

    /**
     * Marker used to fail an open future in {@code finally} so followers are always released. Its cause
     * is never surfaced — followers fall back to running the action on any leader failure — so a shared
     * instance with a class-load-time stack trace is fine and avoids allocating on every call.
     */
    private static final FeatureFetchException LEADER_FAILED = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN, "single-flight leader failed");

    private final ConcurrentHashMap<String, CompletableFuture<T>> inFlight = new ConcurrentHashMap<>();

    private final AtomicInteger participants = new AtomicInteger();

    /**
     * Returns the result of {@code action} for {@code key}, running it once across all callers that
     * overlap in time. A leader runs the action; followers await it and, on leader failure, run it
     * themselves.
     */
    T run(String key, Action<T> action) throws FeatureFetchException {
        participants.incrementAndGet();
        try {
            CompletableFuture<T> mine = new CompletableFuture<>();
            CompletableFuture<T> leader = inFlight.putIfAbsent(key, mine);
            return leader == null ? lead(key, mine, action) : follow(leader, action);
        } finally {
            participants.decrementAndGet();
        }
    }

    private T lead(String key, CompletableFuture<T> mine, Action<T> action) throws FeatureFetchException {
        try {
            T result = action.run();
            mine.complete(result);
            return result;
        } finally {
            mine.completeExceptionally(LEADER_FAILED);
            inFlight.remove(key, mine);
        }
    }

    private T follow(CompletableFuture<T> leader, Action<T> action) throws FeatureFetchException {
        try {
            return leader.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    "Interrupted while awaiting an in-flight fetch"
            );
        } catch (ExecutionException leaderFailed) {
            return action.run();
        }
    }

    /**
     * Number of keys with a fetch currently in flight. For tests.
     */
    int inFlightCount() {
        return inFlight.size();
    }

    /**
     * Number of callers currently inside {@link #run} (leaders plus waiting followers). Lets a test
     * deterministically hold a leader until its followers have joined the same in-flight fetch. For tests.
     */
    int participantCount() {
        return participants.get();
    }

    void clear() {
        inFlight.clear();
    }
}
