package growthbook.sdk.java.cache.redis.stickybucket;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

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
        super(keyPrefix);
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
    }

    @Override
    protected String read(String redisKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(redisKey);
        }
    }

    @Override
    protected void write(String redisKey, String json) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(redisKey, json);
        }
    }

    @Override
    protected List<String> readMany(List<String> redisKeys) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.mget(redisKeys.toArray(new String[0]));
        }
    }
}
