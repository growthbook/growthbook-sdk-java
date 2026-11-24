package growthbook.sdk.java.cache.redis.stickybucket;

import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static growthbook.sdk.java.cache.redis.stickybucket.AbstractRedisStickyBucketService.COMPARE_AND_SET_SCRIPT;
import static growthbook.sdk.java.cache.redis.stickybucket.AbstractRedisStickyBucketService.DEFAULT_KEY_PREFIX;

/**
 * Non-blocking {@link AsyncStickyBucketService} backed by Redis through Lettuce's asynchronous API.
 * No thread is parked while a command is in flight, so it is the service to pair with the
 * multi-user client's {@code CompletableFuture} evaluation methods.
 *
 * <p>It stores the same bytes under the same keys as {@link LettuceStickyBucketService} and
 * {@link JedisStickyBucketService} (see {@link AbstractRedisStickyBucketService} for the key format,
 * the optional TTL and the merge-then-compare-and-set save), so blocking and non-blocking services,
 * and the TypeScript {@code RedisStickyBucketService}, can share one Redis.
 *
 * <p><strong>Failure handling.</strong> A Redis access failure or a malformed payload completes the
 * returned stage exceptionally with a {@link StickyBucketServiceException}; nothing is thrown from
 * the method itself. An absent key is a normal miss ({@code null}), not a failure.
 *
 * <p><strong>Timeouts.</strong> The service applies no timeout of its own. The multi-user client
 * gives up on a call after {@code Options.stickyBucketTimeout}, but that does not cancel the Redis
 * command, and Lettuce does not time out asynchronous commands unless {@code TimeoutOptions} is
 * enabled on the client (it is off by default). Enable it, for example
 * {@code ClientOptions.builder().timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200)))},
 * so an abandoned command is released too.
 *
 * <p><strong>Threading.</strong> Stages complete on Lettuce's event-loop threads. The
 * {@link StatefulRedisConnection} lifecycle is owned by the caller, and the service is safe for
 * concurrent use because the connection is.
 *
 * <pre>{@code
 * Options options = Options.builder()
 *     .asyncStickyBucketService(new LettuceAsyncStickyBucketService(connection))
 *     .stickyBucketIdentifierAttributes(Arrays.asList("id"))
 *     .build();
 * }</pre>
 */
public final class LettuceAsyncStickyBucketService implements AsyncStickyBucketService {

    private final RedisAsyncCommands<String, String> commands;
    private final RedisStickyBucketCodec codec;

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     */
    public LettuceAsyncStickyBucketService(StatefulRedisConnection<String, String> connection) {
        this(connection, DEFAULT_KEY_PREFIX);
    }

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     * @param keyPrefix  namespace applied to every Redis key; empty by default for wire-compatibility
     *                   with the TypeScript service
     */
    public LettuceAsyncStickyBucketService(StatefulRedisConnection<String, String> connection, String keyPrefix) {
        this(connection, keyPrefix, null);
    }

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     * @param ttl        optional per-entry expiry; {@code null} (the default) writes entries without a TTL
     */
    public LettuceAsyncStickyBucketService(StatefulRedisConnection<String, String> connection, Duration ttl) {
        this(connection, DEFAULT_KEY_PREFIX, ttl);
    }

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     * @param keyPrefix  namespace applied to every Redis key; empty by default for wire-compatibility
     *                   with the TypeScript service
     * @param ttl        optional per-entry expiry; {@code null} (the default) writes entries without a
     *                   TTL. When set it must be at least 1 millisecond.
     * @throws IllegalArgumentException if {@code ttl} is zero, negative, or below 1 millisecond
     */
    public LettuceAsyncStickyBucketService(StatefulRedisConnection<String, String> connection,
                                           String keyPrefix,
                                           Duration ttl) {
        this.codec = new RedisStickyBucketCodec(keyPrefix, ttl);
        this.commands = Objects.requireNonNull(connection, "connection").async();
    }

    @Override
    public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
        if (attributeName == null || attributeValue == null) {
            return CompletableFuture.completedFuture(null);
        }
        return read(codec.redisKey(codec.docKey(attributeName, attributeValue))).thenApply(codec::deserialize);
    }

    @Override
    public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument document) {
        if (document == null || document.getAttributeName() == null || document.getAttributeValue() == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (document.getAssignments() == null) {
            return failed(new StickyBucketServiceException(
                    "Sticky bucket assignments document is missing its assignments map"));
        }
        String redisKey = codec.redisKey(codec.docKey(document.getAttributeName(), document.getAttributeValue()));
        return save(redisKey, document, 1);
    }

    /**
     * Reads every requested document with a single {@code MGET}.
     */
    @Override
    public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }
        List<String> logicalKeys = codec.logicalKeys(attributes);
        if (logicalKeys.isEmpty()) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }
        String[] redisKeys = codec.redisKeys(logicalKeys).toArray(new String[0]);
        return redis(() -> commands.mget(redisKeys), "Failed to read sticky bucket assignments from Redis")
                .thenApply(results -> codec.documentsByLogicalKey(logicalKeys, values(results)));
    }

    /**
     * Re-reads the stored document, merges {@code document} over it and installs the result with an
     * atomic compare-and-set, trying again when a competing writer changed the key in between.
     */
    private CompletionStage<Void> save(String redisKey, StickyAssignmentsDocument document, int attempt) {
        return read(redisKey).thenCompose(currentRaw -> {
            String updated = codec.mergedJson(currentRaw, document);
            return compareAndSet(redisKey, currentRaw, updated).thenCompose(written -> {
                if (written) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                if (attempt >= RedisStickyBucketCodec.MAX_SAVE_ATTEMPTS) {
                    return failed(new StickyBucketServiceException(
                            "Failed to persist sticky bucket assignments to Redis after "
                                    + RedisStickyBucketCodec.MAX_SAVE_ATTEMPTS + " concurrent update attempts"));
                }
                return save(redisKey, document, attempt + 1);
            });
        });
    }

    private CompletionStage<String> read(String redisKey) {
        return redis(() -> commands.get(redisKey), "Failed to read sticky bucket assignments from Redis");
    }

    private CompletionStage<Boolean> compareAndSet(String redisKey, String expected, String updated) {
        return redis(() -> commands.<Long>eval(
                        COMPARE_AND_SET_SCRIPT, ScriptOutputType.INTEGER, new String[]{redisKey},
                        expected == null ? "nx" : "cas",
                        expected == null ? "" : expected,
                        updated,
                        codec.ttlMillisArg()),
                "Failed to persist sticky bucket assignments to Redis")
                .thenApply(result -> result != null && result == 1L);
    }

    /**
     * Issues one Redis command and reports any failure, whether thrown while issuing it or delivered
     * through its stage, as a {@link StickyBucketServiceException}.
     */
    private static <T> CompletionStage<T> redis(Supplier<CompletionStage<T>> command, String failureMessage) {
        CompletionStage<T> stage;
        try {
            stage = command.get();
        } catch (RuntimeException e) {
            return failed(new StickyBucketServiceException(failureMessage, e));
        }
        return stage.handle((value, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                throw new StickyBucketServiceException(failureMessage, cause);
            }
            return value;
        });
    }

    private static List<String> values(List<KeyValue<String, String>> results) {
        List<String> values = new ArrayList<>(results == null ? 0 : results.size());
        if (results != null) {
            for (KeyValue<String, String> result : results) {
                values.add(result.hasValue() ? result.getValue() : null);
            }
        }
        return values;
    }

    private static <T> CompletionStage<T> failed(RuntimeException error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
