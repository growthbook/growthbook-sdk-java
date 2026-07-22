package growthbook.sdk.java.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import growthbook.sdk.java.exception.FeatureCacheException;
import growthbook.sdk.java.sandbox.GbCacheManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed implementation of {@link GbCacheManager}.
 */
public final class CaffeineGbCacheManager implements GbCacheManager {

    private final Clock clock;
    private final boolean recordStats;
    private final Cache<String, CaffeineCacheEntry> cache;

    private CaffeineGbCacheManager(CaffeineCacheOptions options) {
        this.cache = createCache(options);
        this.clock = options.getClock();
        this.recordStats = options.isRecordStats();
    }

    public static CaffeineGbCacheManager create() {
        return new CaffeineGbCacheManager(CaffeineCacheOptions.defaults());
    }

    public static CaffeineGbCacheManager create(CaffeineCacheOptions options) {
        return new CaffeineGbCacheManager(options);
    }

    /**
     * @return the shared configuration builder; call
     * {@link CaffeineCacheOptions.Builder#buildManager()} to obtain a configured cache manager
     */
    public static CaffeineCacheOptions.Builder builder() {
        return CaffeineCacheOptions.builder();
    }

    @Override
    public void saveContent(String key, String data) {
        try {
            cache.put(key, new CaffeineCacheEntry(data, clock.millis()));
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to save GrowthBook feature cache entry for key: " + key, e);
        }
    }

    @Override
    public String loadCache(String key) {
        CaffeineCacheEntry entry = lookup(key);
        return entry == null ? null : entry.getData();
    }

    @Override
    public Long getLastUpdatedMillis(String key) {
        CaffeineCacheEntry entry = lookup(key);
        return entry == null ? null : entry.getLastUpdatedMillis();
    }

    /**
     * Reads an entry from the cache, distinguishing the two outcomes callers care about: an absent
     * key is a normal <em>cache miss</em> and returns {@code null}, while a genuine cache-access
     * failure is wrapped in a {@link FeatureCacheException} with the offending key for context.
     *
     * @param key cache key to look up
     * @return the cached entry, or {@code null} when the key is absent
     */
    private CaffeineCacheEntry lookup(String key) {
        try {
            return cache.getIfPresent(key);
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to read GrowthBook feature cache entry for key: " + key, e);
        }
    }

    @Override
    public void clearCache() {
        try {
            cache.invalidateAll();
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to clear GrowthBook feature cache", e);
        }
    }

    /**
     * Returns a snapshot of cache statistics. Counters are meaningful only when statistics
     * recording was enabled via {@link CaffeineCacheOptions.Builder#recordStats(boolean)};
     * otherwise every counter is {@code 0}.
     *
     * @return immutable cache statistics snapshot
     */
    public CaffeineCacheStats stats() {
        CacheStats snapshot = cache.stats();
        return new CaffeineCacheStats(
                recordStats,
                snapshot.hitCount(),
                snapshot.missCount(),
                snapshot.evictionCount()
        );
    }

    /**
     * Performs any pending cache maintenance, such as applying size/weight-based eviction.
     * Eviction is otherwise performed asynchronously; this is primarily useful for deterministic
     * tests and for forcing reclamation on demand.
     */
    public void cleanUp() {
        cache.cleanUp();
    }

    /**
     * Weighs an entry by the UTF-8 byte length of its payload (the cache key is negligible for the
     * single-payload-per-endpoint usage, so only the value contributes to the weight bound).
     */
    private static final Weigher<String, CaffeineCacheEntry> PAYLOAD_WEIGHER = (key, entry) -> {
        String data = entry.getData();
        return data == null ? 0 : data.getBytes(StandardCharsets.UTF_8).length;
    };

    private static Cache<String, CaffeineCacheEntry> createCache(CaffeineCacheOptions options) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        applySizeBound(builder, options);
        applyExpiry(builder, options);
        if (options.isRecordStats()) {
            builder.recordStats();
        }
        if (options.getTicker() != null) {
            builder.ticker(options.getTicker());
        }
        return builder.build();
    }

    private static void applySizeBound(Caffeine<Object, Object> builder, CaffeineCacheOptions options) {
        Long maximumWeight = options.getMaximumWeight();
        if (maximumWeight != null) {
            builder.maximumWeight(maximumWeight).weigher(PAYLOAD_WEIGHER);
        } else {
            builder.maximumSize(options.getMaximumSize());
        }
    }

    private static void applyExpiry(Caffeine<Object, Object> builder, CaffeineCacheOptions options) {
        Duration expireAfterWrite = options.getExpireAfterWrite();
        if (expireAfterWrite != null) {
            builder.expireAfterWrite(expireAfterWrite.toNanos(), TimeUnit.NANOSECONDS);
        }
        Duration expireAfterAccess = options.getExpireAfterAccess();
        if (expireAfterAccess != null) {
            builder.expireAfterAccess(expireAfterAccess.toNanos(), TimeUnit.NANOSECONDS);
        }
    }
}
