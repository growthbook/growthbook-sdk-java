package growthbook.sdk.java.cache.redis.jedis;

import growthbook.sdk.java.cache.redis.AbstractRedisGbCacheManager;
import growthbook.sdk.java.sandbox.GbCacheManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link GbCacheManager} backed by Redis through the Jedis client ({@link JedisPool}).
 *
 * <p>The {@link JedisPool} lifecycle is owned by the caller; the adapter only borrows connections.
 * See {@link AbstractRedisGbCacheManager} for the shared caching behaviour.
 */
public final class JedisGbCacheManager extends AbstractRedisGbCacheManager {

    private final JedisPool jedisPool;

    private JedisGbCacheManager(JedisCacheOptions options) {
        super(options.getKeyPrefix(), options.getTtl(), options.getClock());
        this.jedisPool = options.getJedisPool();
    }

    public static JedisGbCacheManager create(JedisCacheOptions options) {
        return new JedisGbCacheManager(Objects.requireNonNull(options, "options"));
    }

    public static JedisGbCacheManager create(JedisPool jedisPool) {
        return create(JedisCacheOptions.builder().jedisPool(jedisPool).build());
    }

    /**
     * @return the shared configuration builder; call
     * {@link JedisCacheOptions.Builder#buildManager()} to obtain a configured cache manager
     */
    public static JedisCacheOptions.Builder builder() {
        return JedisCacheOptions.builder();
    }

    @Override
    protected void writeHash(String redisKey, Map<String, String> hash, Long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(redisKey, hash);
            if (ttlSeconds != null) {
                jedis.expire(redisKey, ttlSeconds);
            }
        }
    }

    @Override
    protected Map<String, String> readHash(String redisKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(redisKey);
        }
    }

    @Override
    protected void deleteByPrefix(String matchPattern) {
        ScanParams scanParams = new ScanParams().match(matchPattern).count(SCAN_BATCH_SIZE);
        try (Jedis jedis = jedisPool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                ScanResult<String> scan = jedis.scan(cursor, scanParams);
                List<String> keys = scan.getResult();
                if (keys != null && !keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
                cursor = scan.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        }
    }
}
