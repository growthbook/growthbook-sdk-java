package growthbook.sdk.java.cache.redis.jedis;

import growthbook.sdk.java.exception.FeatureCacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JedisGbCacheManagerTest {

    private static final String PREFIX = JedisCacheOptions.DEFAULT_KEY_PREFIX;

    private Jedis jedis;
    private JedisPool pool;

    @BeforeEach
    void setUp() {
        pool = mock(JedisPool.class);
        jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("Verify: saveContent writes a hash with data and updatedAt; no TTL by default")
    void saveContentWritesHashWithTimestamp() {
        // Given
        JedisGbCacheManager cache = JedisGbCacheManager.builder()
                .jedisPool(pool)
                .clock(fixedClock())
                .buildManager();

        // When
        cache.saveContent("FEATURE_CACHE.json", "{\"features\":{}}");

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jedis).hset(eq(PREFIX + "FEATURE_CACHE.json"), hashCaptor.capture());
        Map<String, String> hash = hashCaptor.getValue();
        assertEquals("{\"features\":{}}", hash.get("data"));
        assertEquals("1234", hash.get("updatedAt"));
        verify(jedis, never()).expire(anyString(), anyLong());
    }

    @Test
    @DisplayName("Verify: saveContent applies a TTL when configured")
    void saveContentAppliesTtlWhenConfigured() {
        // Given
        JedisGbCacheManager cache = JedisGbCacheManager.builder()
                .jedisPool(pool)
                .ttl(Duration.ofSeconds(30))
                .buildManager();

        // When
        cache.saveContent("k", "v");

        // Then
        verify(jedis).expire(PREFIX + "k", 30L);
    }

    @Test
    @DisplayName("Verify: loadCache and getLastUpdatedMillis read the stored hash")
    void loadCacheReturnsStoredData() {
        // Given
        Map<String, String> stored = new HashMap<>();
        stored.put("data", "cached");
        stored.put("updatedAt", "555");
        when(jedis.hgetAll(PREFIX + "k")).thenReturn(stored);
        JedisGbCacheManager cache = JedisGbCacheManager.builder().jedisPool(pool).buildManager();

        // Then
        assertEquals("cached", cache.loadCache("k"));
        assertEquals(555L, cache.getLastUpdatedMillis("k"));
    }

    @Test
    @DisplayName("Verify: a missing key is a miss (null content and null timestamp)")
    void returnsNullForMissingEntry() {
        // Given
        when(jedis.hgetAll(anyString())).thenReturn(new HashMap<>());
        JedisGbCacheManager cache = JedisGbCacheManager.builder().jedisPool(pool).buildManager();

        // Then
        assertNull(cache.loadCache("k"));
        assertNull(cache.getLastUpdatedMillis("k"));
    }

    @Test
    @DisplayName("Verify: clearCache scans by prefix and deletes matching keys")
    void clearCacheScansAndDeletesByPrefix() {
        // Given
        ScanResult<String> result = new ScanResult<>(
                ScanParams.SCAN_POINTER_START,
                Arrays.asList(PREFIX + "a", PREFIX + "b")
        );
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(result);
        JedisGbCacheManager cache = JedisGbCacheManager.builder().jedisPool(pool).buildManager();

        // When
        cache.clearCache();

        // Then
        verify(jedis).del(PREFIX + "a", PREFIX + "b");
    }


    @Test
    @DisplayName("Verify: clearCache follows the SCAN cursor across multiple iterations")
    void clearCacheFollowsCursorAcrossIterations() {
        // Given
        ScanResult<String> first = new ScanResult<>("5", Collections.singletonList(PREFIX + "a"));
        ScanResult<String> second = new ScanResult<>(ScanParams.SCAN_POINTER_START,
            Collections.singletonList(PREFIX + "b"));
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(first, second);
        JedisGbCacheManager cache = JedisGbCacheManager.builder().jedisPool(pool).buildManager();

        // When
        cache.clearCache();

        // Then (explicit arrays bind to del(String...) rather than the del(String) overload)
        verify(jedis).del(new String[]{PREFIX + "a"});
        verify(jedis).del(new String[]{PREFIX + "b"});
    }

    @Test
    @DisplayName("Verify: a Redis access failure is wrapped in FeatureCacheException")
    void wrapsFailuresInFeatureCacheException() {
        // Given
        when(jedis.hgetAll(anyString())).thenThrow(new JedisException("boom"));
        JedisGbCacheManager cache = JedisGbCacheManager.builder().jedisPool(pool).buildManager();

        // Then
        assertThrows(FeatureCacheException.class, () -> cache.loadCache("k"));
    }

    @Test
    @DisplayName("Verify: invalid options fail fast")
    void validatesOptions() {
        // Given
        JedisCacheOptions.Builder options = JedisGbCacheManager.builder();

        // When & Then
        assertThrows(NullPointerException.class, () -> JedisGbCacheManager.create((JedisCacheOptions) null));
        assertThrows(NullPointerException.class, () -> options.jedisPool(null));
        assertThrows(IllegalArgumentException.class, () -> options.keyPrefix(" "));
        assertThrows(IllegalArgumentException.class, () -> options.ttl(Duration.ZERO));
        assertThrows(NullPointerException.class, () -> options.clock(null));
        assertThrows(NullPointerException.class, options::build);
    }

    @Test
    @DisplayName("Verify: default key prefix, clock, and no TTL are applied")
    void usesDefaults() {
        // Given
        JedisCacheOptions options = JedisGbCacheManager.builder().jedisPool(pool).build();

        // Then
        assertEquals(JedisCacheOptions.DEFAULT_KEY_PREFIX, options.getKeyPrefix());
        assertNull(options.getTtl());
        assertNotNull(options.getClock());
        assertSame(pool, options.getJedisPool());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochMilli(1234L), ZoneId.of("UTC"));
    }
}
