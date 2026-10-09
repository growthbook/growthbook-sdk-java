package growthbook.sdk.java.cache.redis.lettuce;

import growthbook.sdk.java.cache.redis.AbstractRedisGbCacheManager;
import growthbook.sdk.java.sandbox.GbCacheManager;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.ScriptOutputType;
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

    /**
     * Sets the hash and its expiry atomically in a single server-side command. A {@code MULTI}/{@code EXEC}
     * transaction cannot be used here: {@link #commands} wraps one shared connection, so concurrent
     * operations would interleave into the transaction and an exception between {@code MULTI} and
     * {@code EXEC} would leave it open and poison later commands. {@code ARGV[1]} is the TTL in millis;
     * {@code ARGV[2..]} are the hash field/value pairs.
     */
    private static final String HSET_PEXPIRE_SCRIPT =
            "redis.call('HSET', KEYS[1], unpack(ARGV, 2))\n"
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[1])";

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
    protected void writeHash(String redisKey, Map<String, String> hash, Long ttlMillis) {
        if (ttlMillis == null) {
            commands.hset(redisKey, hash);
            return;
        }
        String[] argv = new String[1 + hash.size() * 2];
        argv[0] = Long.toString(ttlMillis);
        int i = 1;
        for (Map.Entry<String, String> entry : hash.entrySet()) {
            argv[i++] = entry.getKey();
            argv[i++] = entry.getValue();
        }
        commands.eval(HSET_PEXPIRE_SCRIPT, ScriptOutputType.INTEGER, new String[]{redisKey}, argv);
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
