package growthbook.sdk.java.cache.jcache;

import growthbook.sdk.java.exception.FeatureCacheException;
import growthbook.sdk.java.sandbox.GbCacheManager;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import java.time.Clock;
import java.util.Objects;

/**
 * JSR-107 (JCache) backed implementation of {@link GbCacheManager}.
 *
 * <p>Wraps a {@link Cache} obtained from a JCache {@link CacheManager}. The underlying provider
 * (Ehcache, Hazelcast, Caffeine-JCache, the reference implementation, etc.) owns eviction, expiry,
 * and sizing; this adapter only stores feature payloads with their last-updated timestamp.
 */
public final class JCacheGbCacheManager implements GbCacheManager {

    private final Clock clock;
    private final Cache<String, JCacheCacheEntry> cache;

    private JCacheGbCacheManager(JCacheCacheOptions options) {
        this.cache = resolveCache(options);
        this.clock = options.getClock();
    }

    public static JCacheGbCacheManager create(JCacheCacheOptions options) {
        return new JCacheGbCacheManager(Objects.requireNonNull(options, "options"));
    }

    public static JCacheGbCacheManager create(CacheManager cacheManager) {
        return create(JCacheCacheOptions.builder().cacheManager(cacheManager).build());
    }

    /**
     * @return the shared configuration builder; call
     * {@link JCacheCacheOptions.Builder#buildManager()} to obtain a configured cache manager
     */
    public static JCacheCacheOptions.Builder builder() {
        return JCacheCacheOptions.builder();
    }

    @Override
    public void saveContent(String key, String data) {
        try {
            cache.put(key, new JCacheCacheEntry(data, clock.millis()));
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to save GrowthBook feature cache entry for key: " + key, e);
        }
    }

    @Override
    public String loadCache(String key) {
        JCacheCacheEntry entry = lookup(key);
        return entry == null ? null : entry.getData();
    }

    @Override
    public Long getLastUpdatedMillis(String key) {
        JCacheCacheEntry entry = lookup(key);
        return entry == null ? null : entry.getLastUpdatedMillis();
    }

    @Override
    public void clearCache() {
        try {
            cache.clear();
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to clear GrowthBook feature cache", e);
        }
    }

    /**
     * Reads an entry from the cache, distinguishing the two outcomes callers care about: an absent
     * key is a normal <em>cache miss</em> and returns {@code null}, while a genuine cache-access
     * failure is wrapped in a {@link FeatureCacheException} with the offending key for context.
     *
     * @param key cache key to look up
     * @return the cached entry, or {@code null} when the key is absent
     */
    private JCacheCacheEntry lookup(String key) {
        try {
            return cache.get(key);
        } catch (RuntimeException e) {
            throw new FeatureCacheException("Failed to load GrowthBook feature cache entry for key: " + key, e);
        }
    }

    private static Cache<String, JCacheCacheEntry> resolveCache(JCacheCacheOptions options) {
        CacheManager cacheManager = options.getCacheManager();
        String cacheName = options.getCacheName();

        Cache<String, JCacheCacheEntry> existing = cacheManager.getCache(cacheName, String.class, JCacheCacheEntry.class);
        if (existing != null) {
            return existing;
        }

        MutableConfiguration<String, JCacheCacheEntry> configuration =
                new MutableConfiguration<String, JCacheCacheEntry>()
                        .setTypes(String.class, JCacheCacheEntry.class);
        try {
            return cacheManager.createCache(cacheName, configuration);
        } catch (CacheException e) {
            // Another caller created the cache concurrently; reuse it instead of failing.
            Cache<String, JCacheCacheEntry> created = cacheManager.getCache(cacheName, String.class, JCacheCacheEntry.class);
            if (created != null) {
                return created;
            }
            throw new FeatureCacheException("Failed to create JCache feature cache: " + cacheName, e);
        }
    }
}
