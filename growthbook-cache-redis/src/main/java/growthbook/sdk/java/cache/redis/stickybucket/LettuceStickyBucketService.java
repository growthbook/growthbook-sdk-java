package growthbook.sdk.java.cache.redis.stickybucket;

import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link growthbook.sdk.java.stickyBucketing.StickyBucketService} backed by Redis through the
 * Lettuce client.
 *
 * <p>The {@link StatefulRedisConnection} lifecycle is owned by the caller; the adapter issues
 * synchronous commands over it. See {@link AbstractRedisStickyBucketService} for the shared
 * behaviour and the failure contract.
 *
 * <pre>{@code
 * Options options = Options.builder()
 *     .stickyBucketService(new LettuceStickyBucketService(connection))
 *     .stickyBucketIdentifierAttributes(Arrays.asList("id"))
 *     .build();
 * }</pre>
 */
public final class LettuceStickyBucketService extends AbstractRedisStickyBucketService {

    private final RedisCommands<String, String> commands;

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     */
    public LettuceStickyBucketService(StatefulRedisConnection<String, String> connection) {
        this(connection, DEFAULT_KEY_PREFIX);
    }

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     * @param keyPrefix  namespace applied to every Redis key; empty by default for wire-compatibility
     *                   with the TypeScript service
     */
    public LettuceStickyBucketService(StatefulRedisConnection<String, String> connection, String keyPrefix) {
        this(connection, keyPrefix, null);
    }

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     * @param ttl        optional per-entry expiry; {@code null} (the default) writes entries without a TTL
     */
    public LettuceStickyBucketService(StatefulRedisConnection<String, String> connection, Duration ttl) {
        this(connection, DEFAULT_KEY_PREFIX, ttl);
    }

    /**
     * @param connection the Lettuce connection used to reach Redis; owned by the caller
     * @param keyPrefix  namespace applied to every Redis key; empty by default for wire-compatibility
     *                   with the TypeScript service
     * @param ttl        optional per-entry expiry; {@code null} (the default) writes entries without a
     *                   TTL. When set it must be at least 1 millisecond.
     */
    public LettuceStickyBucketService(StatefulRedisConnection<String, String> connection, String keyPrefix, Duration ttl) {
        super(keyPrefix, ttl);
        this.commands = Objects.requireNonNull(connection, "connection").sync();
    }

    @Override
    protected String read(String redisKey) {
        return commands.get(redisKey);
    }

    @Override
    protected boolean compareAndSet(String redisKey, String expected, String updated) {
        Long result = commands.eval(
                COMPARE_AND_SET_SCRIPT, ScriptOutputType.INTEGER, new String[]{redisKey},
                expected == null ? "nx" : "cas",
                expected == null ? "" : expected,
                updated,
                ttlMillisArg());
        return result != null && result == 1L;
    }

    @Override
    protected List<String> readMany(List<String> redisKeys) {
        List<KeyValue<String, String>> results = commands.mget(redisKeys.toArray(new String[0]));
        List<String> values = new ArrayList<>(results.size());
        for (KeyValue<String, String> kv : results) {
            values.add(kv.hasValue() ? kv.getValue() : null);
        }
        return values;
    }
}
