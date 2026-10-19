package growthbook.sdk.java.cache.redis.stickybucket;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * {@link growthbook.sdk.java.stickyBucketing.StickyBucketService} backed by Redis through the Jedis
 * client ({@link JedisPool}).
 *
 * <p>The {@link JedisPool} lifecycle is owned by the caller; the adapter only borrows connections.
 * See {@link AbstractRedisStickyBucketService} for the shared behaviour and the failure contract.
 *
 * <pre>{@code
 * Options options = Options.builder()
 *     .stickyBucketService(new JedisStickyBucketService(jedisPool))
 *     .stickyBucketIdentifierAttributes(Arrays.asList("id"))
 *     .build();
 * }</pre>
 */
public final class JedisStickyBucketService extends AbstractRedisStickyBucketService {

    private final JedisPool jedisPool;

    /**
     * @param jedisPool the Jedis connection pool used to reach Redis; owned by the caller
     */
    public JedisStickyBucketService(JedisPool jedisPool) {
        this(jedisPool, DEFAULT_KEY_PREFIX);
    }

    /**
     * @param jedisPool the Jedis connection pool used to reach Redis; owned by the caller
     * @param keyPrefix namespace applied to every Redis key; empty by default for wire-compatibility
     *                  with the TypeScript service
     */
    public JedisStickyBucketService(JedisPool jedisPool, String keyPrefix) {
        this(jedisPool, keyPrefix, null);
    }

    /**
     * @param jedisPool the Jedis connection pool used to reach Redis; owned by the caller
     * @param ttl       optional per-entry expiry; {@code null} (the default) writes entries without a TTL
     */
    public JedisStickyBucketService(JedisPool jedisPool, Duration ttl) {
        this(jedisPool, DEFAULT_KEY_PREFIX, ttl);
    }

    /**
     * @param jedisPool the Jedis connection pool used to reach Redis; owned by the caller
     * @param keyPrefix namespace applied to every Redis key; empty by default for wire-compatibility
     *                  with the TypeScript service
     * @param ttl       optional per-entry expiry; {@code null} (the default) writes entries without a
     *                  TTL. When set it must be at least 1 millisecond.
     */
    public JedisStickyBucketService(JedisPool jedisPool, String keyPrefix, Duration ttl) {
        super(keyPrefix, ttl);
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
    }

    @Override
    protected String read(String redisKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(redisKey);
        }
    }

    @Override
    protected boolean compareAndSet(String redisKey, String expected, String updated) {
        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(
                    COMPARE_AND_SET_SCRIPT, 1, redisKey,
                    expected == null ? "nx" : "cas",
                    expected == null ? "" : expected,
                    updated,
                    ttlMillisArg());
            return result instanceof Long && (Long) result == 1L;
        }
    }

    @Override
    protected List<String> readMany(List<String> redisKeys) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.mget(redisKeys.toArray(new String[0]));
        }
    }
}
