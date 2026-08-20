package growthbook.sdk.java.cache.redis;

import growthbook.sdk.java.exception.FeatureCacheException;
import growthbook.sdk.java.sandbox.GbCacheManager;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Shared {@link GbCacheManager} behaviour for Redis adapters, independent of the Redis client.
 *
 * <p>This base owns the common algorithm — key namespacing, hash &harr; entry mapping, cache-miss
 * vs. failure handling, and {@link FeatureCacheException} wrapping — and delegates the
 * client-specific primitives ({@link #writeHash}, {@link #readHash}, {@link #readHashField},
 * {@link #deleteByPrefix}) to subclasses. Concrete subclasses therefore only translate those
 * primitives to their client's API.
 *
 * <p><strong>Redis Cluster is not supported.</strong> The adapters target standalone and
 * Sentinel deployments: {@link #clearCache()} issues multi-key {@code DEL} commands, which fail
 * with {@code CROSSSLOT} when the matched keys hash to different cluster slots, and a plain
 * {@code SCAN} only iterates the node the connection points at. If you must run against a
 * cluster, configure a hash-tag key prefix (for example {@code "{growthbook}:features:"}) so
 * every adapter key maps to the same slot, and be aware that other keys on other nodes are not
 * scanned.
 */
public abstract class AbstractRedisGbCacheManager implements GbCacheManager {

    /** Number of keys requested per SCAN iteration during {@link #clearCache()}. */
    protected static final int SCAN_BATCH_SIZE = 256;

    private final Clock clock;
    private final Long ttlMillis;
    private final String keyPrefix;

    protected AbstractRedisGbCacheManager(String keyPrefix, Duration ttl, Clock clock) {
        this.keyPrefix = keyPrefix;
        this.clock = clock;
        this.ttlMillis = ttl == null ? null : ttl.toMillis();
    }

    @Override
    public void saveContent(String key, String data) {
        Map<String, String> hash = RedisCacheEntry.toHash(data, clock.millis());
        try {
            writeHash(redisKey(key), hash, ttlMillis);
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to save GrowthBook feature cache entry for key: " + key, e);
        }
    }

    @Override
    public String loadCache(String key) {
        RedisCacheEntry entry = lookup(key);
        return entry == null ? null : entry.getData();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads only the timestamp field ({@code HGET}) instead of the whole hash, so freshness
     * checks do not transfer the potentially large feature payload.
     */
    @Override
    public Long getLastUpdatedMillis(String key) {
        String timestamp;
        try {
            timestamp = readHashField(redisKey(key), RedisCacheEntry.FIELD_UPDATED_AT);
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to load GrowthBook feature cache entry for key: " + key, e);
        }
        return timestamp == null ? null : RedisCacheEntry.parseTimestamp(timestamp);
    }

    @Override
    public void clearCache() {
        try {
            deleteByPrefix(keyPrefix + "*");
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to clear GrowthBook feature cache", e);
        }
    }

    /**
     * Reads an entry, distinguishing the two outcomes callers care about: a missing key is a normal
     * <em>cache miss</em> and returns {@code null}, while a genuine cache-access failure is wrapped
     * in a {@link FeatureCacheException} with the offending key for context.
     */
    private RedisCacheEntry lookup(String key) {
        try {
            return RedisCacheEntry.fromHash(readHash(redisKey(key)));
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to load GrowthBook feature cache entry for key: " + key, e);
        }
    }

    /**
     * @param key the cache key supplied by the SDK
     * @return the namespaced Redis key
     */
    protected final String redisKey(String key) {
        return keyPrefix + key;
    }

    /**
     * Writes the hash fields for a key, applying the TTL (in milliseconds, via {@code PEXPIRE})
     * when non-null.
     */
    protected abstract void writeHash(String redisKey, Map<String, String> hash, Long ttlMillis);

    /**
     * @return the hash fields stored for a key, or an empty/{@code null} map when absent
     */
    protected abstract Map<String, String> readHash(String redisKey);

    /**
     * @return the value of a single hash field for a key, or {@code null} when the key or field
     * is absent
     */
    protected abstract String readHashField(String redisKey, String field);

    /**
     * Deletes every key matching the glob pattern (the adapter's namespace).
     */
    protected abstract void deleteByPrefix(String matchPattern);
}
