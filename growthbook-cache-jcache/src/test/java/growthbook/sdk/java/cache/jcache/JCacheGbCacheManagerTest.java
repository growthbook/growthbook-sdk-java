package growthbook.sdk.java.cache.jcache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JCacheGbCacheManagerTest {

    private CachingProvider cachingProvider;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cachingProvider = Caching.getCachingProvider();
        cacheManager = cachingProvider.getCacheManager();
    }

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    @Test
    @DisplayName("Verify: cached content is saved with a last-updated timestamp and can be loaded")
    void savesAndLoadsContent() {
        // Given
        JCacheGbCacheManager cache = JCacheGbCacheManager.builder().cacheManager(cacheManager).buildManager();

        // When
        cache.saveContent("features", "{\"features\":{}}");

        // Then
        assertEquals("{\"features\":{}}", cache.loadCache("features"));
        assertNotNull(cache.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: missing entries return null content and null last-updated timestamp")
    void returnsNullForMissingEntry() {
        // Given
        JCacheGbCacheManager cache = JCacheGbCacheManager.builder().cacheManager(cacheManager).buildManager();

        // Then
        assertNull(cache.loadCache("features"));
        assertNull(cache.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: clearing cache removes stored content and timestamps")
    void clearsEntries() {
        // Given
        JCacheGbCacheManager cache = JCacheGbCacheManager.builder().cacheManager(cacheManager).buildManager();
        cache.saveContent("features", "cached");

        // When
        cache.clearCache();

        // Then
        assertNull(cache.loadCache("features"));
        assertNull(cache.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: overwriting content replaces the cached value and timestamp")
    void overwritesContentAndTimestamp() {
        // Given
        MutableClock clock = new MutableClock(1000L);
        JCacheGbCacheManager cache = JCacheGbCacheManager.builder()
                .cacheManager(cacheManager)
                .clock(clock)
                .buildManager();

        // When
        cache.saveContent("features", "first");
        clock.setMillis(2000L);
        cache.saveContent("features", "second");

        // Then
        assertEquals("second", cache.loadCache("features"));
        assertEquals(2000L, cache.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: invalid options fail fast")
    void validatesOptions() {
        // Given
        JCacheCacheOptions.Builder options = JCacheGbCacheManager.builder();

        // When & Then
        assertThrows(NullPointerException.class, () -> JCacheGbCacheManager.create((JCacheCacheOptions) null));
        assertThrows(NullPointerException.class, () -> options.cacheManager(null));
        assertThrows(IllegalArgumentException.class, () -> options.cacheName(" "));
        assertThrows(NullPointerException.class, () -> options.clock(null));
        assertThrows(NullPointerException.class, () -> options.build());
    }

    @Test
    @DisplayName("Verify: default cache name and clock are applied")
    void usesDefaults() {
        // Given
        JCacheCacheOptions options = JCacheGbCacheManager.builder().cacheManager(cacheManager).build();

        // Then
        assertEquals(JCacheCacheOptions.DEFAULT_CACHE_NAME, options.getCacheName());
        assertNotNull(options.getClock());
        assertNotNull(options.getCacheManager());
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;
        private final ZoneId zone;

        private MutableClock(long millis) {
            this(millis, ZoneId.of("UTC"));
        }

        private MutableClock(long millis, ZoneId zone) {
            this.millis = new AtomicLong(millis);
            this.zone = zone;
        }

        void setMillis(long millis) {
            this.millis.set(millis);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(millis.get(), zone);
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
