package growthbook.sdk.java.cache.caffeine;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineGbCacheManagerTest {

    @Test
    @DisplayName("Verify: cached content is saved with a last-updated timestamp and can be loaded")
    void savesAndLoadsContent() {
        // Given
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.create();

        // When
        cacheManager.saveContent("features", "{\"features\":{}}");

        // Then
        assertEquals("{\"features\":{}}", cacheManager.loadCache("features"));
        assertNotNull(cacheManager.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: missing cache entries return null content and null last-updated timestamp")
    void returnsNullForMissingEntry() {
        // Given
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.create();

        // Then
        assertNull(cacheManager.loadCache("features"));
        assertNull(cacheManager.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: clearing cache removes stored content and last-updated timestamps")
    void clearsEntriesAndTimestamps() {
        // Given
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.create();
        cacheManager.saveContent("features", "cached");

        // When
        cacheManager.clearCache();

        // Then
        assertNull(cacheManager.loadCache("features"));
        assertNull(cacheManager.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: overwriting content replaces the cached value and timestamp")
    void overwritesContentAndLastUpdatedTime() {
        // Given
        MutableClock clock = new MutableClock(1000L);
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.builder()
                .clock(clock)
                .buildManager();

        // When
        cacheManager.saveContent("features", "first");
        clock.setMillis(2000L);
        cacheManager.saveContent("features", "second");

        // Then
        assertEquals("second", cacheManager.loadCache("features"));
        assertEquals(2000L, cacheManager.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: entries expire when expire-after-write is configured")
    void expiresEntriesWhenExpireAfterWriteIsConfigured() {
        // Given
        MutableTicker ticker = new MutableTicker();
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.builder()
                .expireAfterWrite(Duration.ofMillis(10))
                .ticker(ticker)
                .buildManager();

        // When
        cacheManager.saveContent("features", "cached");
        ticker.advance(Duration.ofMillis(11));

        // Then
        assertNull(cacheManager.loadCache("features"));
        assertNull(cacheManager.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: invalid Caffeine cache options fail fast")
    void validatesOptions() {
        // Given
        CaffeineCacheOptions.Builder options = CaffeineCacheOptions.builder();

        // When & Then
        assertThrows(NullPointerException.class, () -> CaffeineGbCacheManager.create(null));
        assertThrows(IllegalArgumentException.class, () -> options.maximumSize(0));
        assertThrows(IllegalArgumentException.class, () -> options.maximumWeight(0));
        assertThrows(IllegalArgumentException.class, () -> options.expireAfterWrite(Duration.ZERO));
        assertThrows(NullPointerException.class, () -> options.clock(null));
        assertThrows(NullPointerException.class, () -> options.ticker(null));
    }

    @Test
    @DisplayName("Verify: default Caffeine cache options are usable")
    void usesDefaultOptions() {
        CaffeineCacheOptions options = CaffeineCacheOptions.defaults();

        assertEquals(CaffeineCacheOptions.DEFAULT_MAXIMUM_SIZE, options.getMaximumSize());
        assertNull(options.getExpireAfterWrite());
        assertNotNull(options.getClock());
        assertNull(options.getTicker());
        assertTrue(options.getClock().millis() > 0);
    }

    @Test
    @DisplayName("Verify: hit/miss statistics are recorded when stats recording is enabled")
    void recordsHitAndMissStatisticsWhenEnabled() {
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.builder()
                .recordStats(true)
                .buildManager();

        cacheManager.saveContent("features", "cached");
        cacheManager.loadCache("features");
        cacheManager.loadCache("absent");

        CaffeineCacheStats stats = cacheManager.stats();
        assertTrue(stats.isRecording());
        assertEquals(1L, stats.getHitCount());
        assertEquals(1L, stats.getMissCount());
        assertEquals(2L, stats.getRequestCount());
        assertEquals(0.5, stats.getHitRate());
    }

    @Test
    @DisplayName("Verify: statistics are inert (zero, not recording) by default")
    void statisticsAreInertWhenRecordingDisabled() {
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.create();

        cacheManager.saveContent("features", "cached");
        cacheManager.loadCache("features");
        cacheManager.loadCache("absent");

        CaffeineCacheStats stats = cacheManager.stats();
        assertFalse(stats.isRecording());
        assertEquals(0L, stats.getHitCount());
        assertEquals(0L, stats.getMissCount());
        assertEquals(0L, stats.getRequestCount());
        assertEquals(1.0, stats.getHitRate());
    }

    @Test
    @DisplayName("Verify: entries expire when expire-after-access is configured")
    void expiresEntriesWhenExpireAfterAccessIsConfigured() {
        MutableTicker ticker = new MutableTicker();
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.builder()
                .expireAfterAccess(Duration.ofMillis(10))
                .ticker(ticker)
                .buildManager();

        cacheManager.saveContent("features", "cached");
        ticker.advance(Duration.ofMillis(11));

        assertNull(cacheManager.loadCache("features"));
        assertNull(cacheManager.getLastUpdatedMillis("features"));
    }

    @Test
    @DisplayName("Verify: new options expose safe defaults and validate input")
    void newOptionsHaveDefaultsAndValidation() {
        // Given
        CaffeineCacheOptions defaults = CaffeineCacheOptions.defaults();
        CaffeineCacheOptions.Builder options = CaffeineCacheOptions.builder();
        Duration negativeDuration = Duration.ofMillis(-1);

        // When & Then
        assertNull(defaults.getExpireAfterAccess());
        assertNull(defaults.getMaximumWeight());
        assertFalse(defaults.isRecordStats());
        assertThrows(IllegalArgumentException.class, () -> options.expireAfterAccess(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> options.expireAfterAccess(negativeDuration));
    }

    @Test
    @DisplayName("Verify: weight-based bounding evicts by total payload size, not entry count")
    void evictsByWeightWhenMaximumWeightConfigured() {
        // Given: room for roughly one small payload (10 bytes)
        CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.builder()
                .maximumWeight(10)
                .recordStats(true)
                .buildManager();

        // When: two 8-byte payloads (16 bytes total) exceed the weight bound
        cacheManager.saveContent("a", "01234567");
        cacheManager.saveContent("b", "89abcdef");
        cacheManager.cleanUp();

        // Then: at least one entry was evicted to honour the weight bound
        assertTrue(cacheManager.stats().getEvictionCount() >= 1);
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

    private static final class MutableTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }
}
