package growthbook.sdk.java.cache.jcache;

import javax.cache.CacheManager;
import java.time.Clock;
import java.util.Objects;

/**
 * Configuration for {@link JCacheGbCacheManager}.
 *
 * <p>Unlike an embedded cache, a JSR-107 cache is created and configured by its provider, so the
 * adapter is given a {@link CacheManager} plus the name of the cache to use. Eviction, expiry, and
 * sizing are the provider's responsibility and are configured on the provider side.
 */
public final class JCacheCacheOptions {

    public static final String DEFAULT_CACHE_NAME = "growthbook-features";

    private final CacheManager cacheManager;
    private final String cacheName;
    private final Clock clock;

    private JCacheCacheOptions(Builder builder) {
        this.cacheManager = builder.cacheManager;
        this.cacheName = builder.cacheName;
        this.clock = builder.clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public String getCacheName() {
        return cacheName;
    }

    public Clock getClock() {
        return clock;
    }

    public static final class Builder {
        private CacheManager cacheManager;
        private String cacheName = DEFAULT_CACHE_NAME;
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        /**
         * The JSR-107 cache manager used to obtain (or create) the feature cache. Required.
         */
        public Builder cacheManager(CacheManager cacheManager) {
            this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
            return this;
        }

        /**
         * Name of the cache within the {@link CacheManager}. Defaults to {@value #DEFAULT_CACHE_NAME}.
         */
        public Builder cacheName(String cacheName) {
            if (cacheName == null || cacheName.trim().isEmpty()) {
                throw new IllegalArgumentException("cacheName must not be null or blank");
            }
            this.cacheName = cacheName;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public JCacheCacheOptions build() {
            Objects.requireNonNull(cacheManager, "cacheManager must be provided");
            return new JCacheCacheOptions(this);
        }

        /**
         * Convenience terminal that builds these options into a ready-to-use cache manager.
         *
         * @return a {@link JCacheGbCacheManager} configured with these options
         */
        public JCacheGbCacheManager buildManager() {
            return JCacheGbCacheManager.create(build());
        }
    }
}
