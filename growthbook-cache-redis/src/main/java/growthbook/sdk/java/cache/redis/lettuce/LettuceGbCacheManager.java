package growthbook.sdk.java.cache.redis.lettuce;

import growthbook.sdk.java.cache.redis.AbstractRedisGbCacheManager;
import growthbook.sdk.java.sandbox.GbCacheManager;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link GbCacheManager} backed by Redis through the Lettuce client.
 *
 * <p>The {@link StatefulRedisConnection} lifecycle is owned by the caller; the adapter issues
 * synchronous commands over it. See {@link AbstractRedisGbCacheManager} for the shared caching
 * behaviour.
 */
public final class LettuceGbCacheManager extends AbstractRedisGbCacheManager {

    private final RedisCommands<String, String> commands;

    private LettuceGbCacheManager(LettuceCacheOptions options) {
        super(options.getKeyPrefix(), options.getTtl(), options.getClock());
        this.commands = options.getConnection().sync();
    }

    public static LettuceGbCacheManager create(LettuceCacheOptions options) {
        return new LettuceGbCacheManager(Objects.requireNonNull(options, "options"));
    }

    public static LettuceGbCacheManager create(StatefulRedisConnection<String, String> connection) {
        return create(LettuceCacheOptions.builder().connection(connection).build());
    }

    /**
     * @return the shared configuration builder; call
     * {@link LettuceCacheOptions.Builder#buildManager()} to obtain a configured cache manager
     */
    public static LettuceCacheOptions.Builder builder() {
        return LettuceCacheOptions.builder();
    }

    @Override
    protected void writeHash(String redisKey, Map<String, String> hash, Long ttlSeconds) {
        commands.hset(redisKey, hash);
        if (ttlSeconds != null) {
            commands.expire(redisKey, ttlSeconds);
        }
    }

    @Override
    protected Map<String, String> readHash(String redisKey) {
        return commands.hgetall(redisKey);
    }

    @Override
    protected String readHashField(String redisKey, String field) {
        return commands.hget(redisKey, field);
    }

    @Override
    protected void deleteByPrefix(String matchPattern) {
        ScanArgs scanArgs = ScanArgs.Builder.matches(matchPattern).limit(SCAN_BATCH_SIZE);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> scan = commands.scan(cursor, scanArgs);
            List<String> keys = scan.getKeys();
            if (keys != null && !keys.isEmpty()) {
                commands.del(keys.toArray(new String[0]));
            }
            cursor = scan;
        } while (!cursor.isFinished());
    }
}
