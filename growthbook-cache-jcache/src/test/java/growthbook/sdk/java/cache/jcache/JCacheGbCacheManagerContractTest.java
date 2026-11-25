package growthbook.sdk.java.cache.jcache;

import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.GbCacheManagerContractTest;
import org.junit.jupiter.api.AfterEach;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;

class JCacheGbCacheManagerContractTest extends GbCacheManagerContractTest {

    private CacheManager cacheManager;

    @Override
    protected GbCacheManager createCacheManager() {
        cacheManager = Caching.getCachingProvider().getCacheManager();
        return JCacheGbCacheManager.create(JCacheCacheOptions.builder()
                .cacheManager(cacheManager)
                .clock(clock())
                .build());
    }

    @Override
    protected ForeignStore sharedForeignStore() {
        final Cache<String, String> foreignCache = cacheManager.createCache("foreign-cache",
                new MutableConfiguration<String, String>().setTypes(String.class, String.class));
        return new ForeignStore() {
            @Override
            public void write(String key, String value) {
                foreignCache.put(key, value);
            }

            @Override
            public String read(String key) {
                return foreignCache.get(key);
            }
        };
    }

    @Override
    protected boolean failStorage() {
        cacheManager.close();
        return true;
    }

    @AfterEach
    void closeCacheManager() {
        cacheManager.close();
    }
}
