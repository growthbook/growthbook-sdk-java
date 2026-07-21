package growthbook.sdk.java.cache.redis.lettuce;

import growthbook.sdk.java.exception.FeatureCacheException;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisException;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LettuceGbCacheManagerTest {

    private static final String PREFIX = LettuceCacheOptions.DEFAULT_KEY_PREFIX;

    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = mock(StatefulRedisConnection.class);
        commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
    }

    @Test
    @DisplayName("Verify: saveContent writes a hash with data and updatedAt; no TTL by default")
    void saveContentWritesHashWithTimestamp() {
        // Given
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder()
                .connection(connection)
                .clock(fixedClock())
                .buildManager();

        // When
        cache.saveContent("FEATURE_CACHE.json", "{\"features\":{}}");

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(commands).hset(eq(PREFIX + "FEATURE_CACHE.json"), hashCaptor.capture());
        Map<String, String> hash = hashCaptor.getValue();
        assertEquals("{\"features\":{}}", hash.get("data"));
        assertEquals("1234", hash.get("updatedAt"));
        verify(commands, never()).pexpire(anyString(), anyLong());
    }

    @Test
    @DisplayName("Verify: saveContent applies a TTL (in milliseconds) when configured")
    void saveContentAppliesTtlWhenConfigured() {
        // Given
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder()
                .connection(connection)
                .ttl(Duration.ofSeconds(30))
                .buildManager();

        // When
        cache.saveContent("k", "v");

        // Then
        verify(commands).pexpire(PREFIX + "k", 30_000L);
    }

    @Test
    @DisplayName("Verify: a sub-second TTL is honoured with millisecond precision, not truncated to 0")
    void saveContentHonoursSubSecondTtl() {
        // Given
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder()
                .connection(connection)
                .ttl(Duration.ofMillis(500))
                .buildManager();

        // When
        cache.saveContent("k", "v");

        // Then: PEXPIRE with 500ms, never EXPIRE 0 (which would immediately delete the entry)
        verify(commands).pexpire(PREFIX + "k", 500L);
        verify(commands, never()).expire(anyString(), anyLong());
    }

    @Test
    @DisplayName("Verify: loadCache and getLastUpdatedMillis read the stored hash")
    void loadCacheReturnsStoredData() {
        // Given
        Map<String, String> stored = new HashMap<>();
        stored.put("data", "cached");
        stored.put("updatedAt", "555");
        when(commands.hgetall(PREFIX + "k")).thenReturn(stored);
        when(commands.hget(PREFIX + "k", "updatedAt")).thenReturn("555");
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder().connection(connection).buildManager();

        // Then
        assertEquals("cached", cache.loadCache("k"));
        assertEquals(555L, cache.getLastUpdatedMillis("k"));
    }

    @Test
    @DisplayName("Verify: getLastUpdatedMillis reads only the timestamp field, not the payload")
    void getLastUpdatedMillisReadsSingleField() {
        // Given
        when(commands.hget(PREFIX + "k", "updatedAt")).thenReturn("555");
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder().connection(connection).buildManager();

        // When
        Long lastUpdated = cache.getLastUpdatedMillis("k");

        // Then
        assertEquals(555L, lastUpdated);
        verify(commands, never()).hgetall(anyString());
    }

    @Test
    @DisplayName("Verify: a missing key is a miss (null content and null timestamp)")
    void returnsNullForMissingEntry() {
        // Given
        when(commands.hgetall(anyString())).thenReturn(new HashMap<>());
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder().connection(connection).buildManager();

        // Then
        assertNull(cache.loadCache("k"));
        assertNull(cache.getLastUpdatedMillis("k"));
    }

    @Test
    @DisplayName("Verify: clearCache scans by prefix and deletes matching keys")
    void clearCacheScansAndDeletesByPrefix() {
        // Given
        KeyScanCursor<String> cursor = new KeyScanCursor<>();
        cursor.setCursor("0");
        cursor.setFinished(true);
        cursor.getKeys().addAll(Arrays.asList(PREFIX + "a", PREFIX + "b"));
        when(commands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(cursor);
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder().connection(connection).buildManager();

        // When
        cache.clearCache();

        // Then
        verify(commands).del(PREFIX + "a", PREFIX + "b");
    }

    @Test
    @DisplayName("Verify: clearCache follows the SCAN cursor across multiple iterations")
    void clearCacheFollowsCursorAcrossIterations() {
        // Given
        KeyScanCursor<String> first = new KeyScanCursor<>();
        first.setCursor("5");
        first.setFinished(false);
        first.getKeys().add(PREFIX + "a");
        KeyScanCursor<String> second = new KeyScanCursor<>();
        second.setCursor("0");
        second.setFinished(true);
        second.getKeys().add(PREFIX + "b");
        when(commands.scan(any(ScanCursor.class), any(ScanArgs.class))).thenReturn(first, second);
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder().connection(connection).buildManager();

        // When
        cache.clearCache();

        // Then
        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(commands, times(2)).del(deleted.capture());
        assertEquals(Arrays.asList(PREFIX + "a", PREFIX + "b"), deleted.getAllValues());
    }

    @Test
    @DisplayName("Verify: a Redis access failure is wrapped in FeatureCacheException")
    void wrapsFailuresInFeatureCacheException() {
        // Given
        when(commands.hgetall(anyString())).thenThrow(new RedisException("boom"));
        when(commands.hget(anyString(), anyString())).thenThrow(new RedisException("boom"));
        LettuceGbCacheManager cache = LettuceGbCacheManager.builder().connection(connection).buildManager();

        // Then
        assertThrows(FeatureCacheException.class, () -> cache.loadCache("k"));
        assertThrows(FeatureCacheException.class, () -> cache.getLastUpdatedMillis("k"));
    }

    @Test
    @DisplayName("Verify: invalid options fail fast")
    void validatesOptions() {
        // Given
        LettuceCacheOptions.Builder options = LettuceGbCacheManager.builder();

        // When & Then
        assertThrows(NullPointerException.class, () -> LettuceGbCacheManager.create((LettuceCacheOptions) null));
        assertThrows(NullPointerException.class, () -> options.connection(null));
        assertThrows(IllegalArgumentException.class, () -> options.keyPrefix(" "));
        assertThrows(IllegalArgumentException.class, () -> options.ttl(Duration.ZERO));
        assertThrows(NullPointerException.class, () -> options.clock(null));
        assertThrows(NullPointerException.class, options::build);
    }

    @Test
    @DisplayName("Verify: default key prefix, clock, and no TTL are applied")
    void usesDefaults() {
        // Given
        LettuceCacheOptions options = LettuceGbCacheManager.builder().connection(connection).build();

        // Then
        assertEquals(LettuceCacheOptions.DEFAULT_KEY_PREFIX, options.getKeyPrefix());
        assertNull(options.getTtl());
        assertNotNull(options.getClock());
        assertSame(connection, options.getConnection());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochMilli(1234L), ZoneId.of("UTC"));
    }
}
