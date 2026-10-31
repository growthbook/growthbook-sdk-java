package growthbook.sdk.java.repository.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.sandbox.GbCacheManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeatureCacheStoreTest {

    private static final String CACHE_KEY = "FEATURE_CACHE_0123456789abcdef.json";
    private static final String PAYLOAD = "{\"flag-a\":{\"defaultValue\":true}}";

    @Test
    @DisplayName("Saves and loads the payload under its cache key")
    void savesAndLoadsPayloadUnderCacheKey() {
        // Given
        RecordingCacheManager manager = new RecordingCacheManager();
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, manager);

        // When
        store.saveQuietly(PAYLOAD);

        // Then
        assertEquals(PAYLOAD, manager.entries.get(CACHE_KEY));
        assertEquals(PAYLOAD, store.load());
        assertEquals(Long.valueOf(42L), store.lastUpdatedMillis());
        assertSame(manager, store.manager());
        assertFalse(store.isDisabled());
    }

    @Test
    @DisplayName("Returns no payload and no timestamp on a cache miss")
    void returnsNullOnMiss() {
        // Given
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, new RecordingCacheManager());

        // When
        String loaded = store.load();
        Long updatedAt = store.lastUpdatedMillis();

        // Then
        assertNull(loaded);
        assertNull(updatedAt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("Treats a blank cached payload as no payload")
    void treatsBlankPayloadAsMiss(String blank) {
        // Given
        RecordingCacheManager manager = new RecordingCacheManager();
        manager.entries.put(CACHE_KEY, blank);
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, manager);

        // When
        String loaded = store.load();

        // Then
        assertNull(loaded);
    }

    @Test
    @DisplayName("Never touches the manager when caching is disabled")
    void disabledStoreNeverTouchesManager() {
        // Given
        RecordingCacheManager manager = new RecordingCacheManager();
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, true, manager);

        // When
        store.saveQuietly(PAYLOAD);
        String loaded = store.load();
        Long updatedAt = store.lastUpdatedMillis();

        // Then
        assertTrue(store.isDisabled());
        assertNull(store.manager());
        assertNull(loaded);
        assertNull(updatedAt);
        assertEquals(0, manager.calls);
    }

    @Test
    @DisplayName("Ignores a new manager when caching is disabled")
    void disabledStoreIgnoresSetManager() {
        // Given
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, true, null);
        RecordingCacheManager manager = new RecordingCacheManager();

        // When
        store.setManager(manager);
        store.saveQuietly(PAYLOAD);

        // Then
        assertNull(store.manager());
        assertEquals(0, manager.calls);
    }

    @Test
    @DisplayName("Uses a replacement manager when caching is enabled")
    void enabledStoreUsesReplacementManager() {
        // Given
        RecordingCacheManager original = new RecordingCacheManager();
        RecordingCacheManager replacement = new RecordingCacheManager();
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, original);

        // When
        store.setManager(replacement);
        store.saveQuietly(PAYLOAD);

        // Then
        assertSame(replacement, store.manager());
        assertEquals(PAYLOAD, replacement.entries.get(CACHE_KEY));
        assertEquals(0, original.calls);
    }

    @Test
    @DisplayName("Treats a manager that throws on load as a miss")
    void throwingLoadIsMiss() {
        // Given
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, new ThrowingCacheManager());

        // When
        String loaded = assertDoesNotThrow(store::load);

        // Then
        assertNull(loaded);
    }

    @Test
    @DisplayName("Treats a manager that throws on the timestamp as unknown freshness")
    void throwingTimestampIsUnknown() {
        // Given
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, new ThrowingCacheManager());

        // When
        Long updatedAt = assertDoesNotThrow(store::lastUpdatedMillis);

        // Then
        assertNull(updatedAt);
    }

    @Test
    @DisplayName("Swallows a manager failure on save")
    void throwingSaveIsSwallowed() {
        // Given
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, new ThrowingCacheManager());

        // When
        // Then
        assertDoesNotThrow(() -> store.saveQuietly(PAYLOAD));
    }

    @Test
    @DisplayName("Drops the manager on release without clearing the cache")
    void releaseDropsManagerWithoutClearing() {
        // Given
        RecordingCacheManager manager = new RecordingCacheManager();
        manager.entries.put(CACHE_KEY, PAYLOAD);
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, manager);

        // When
        store.release();

        // Then
        assertNull(store.manager());
        assertNull(store.load());
        assertEquals(0, manager.clearCalls);
        assertEquals(PAYLOAD, manager.entries.get(CACHE_KEY));
    }

    @Test
    @DisplayName("Treats a second release as a no-op")
    void secondReleaseIsNoOp() {
        // Given
        FeatureCacheStore store = new FeatureCacheStore(CACHE_KEY, false, new RecordingCacheManager());
        store.release();

        // When
        // Then
        assertDoesNotThrow(store::release);
        assertNull(store.manager());
    }

    private static final class RecordingCacheManager implements GbCacheManager {
        private final Map<String, String> entries = new HashMap<>();
        private int calls;
        private int clearCalls;

        @Override
        public void saveContent(String key, String data) {
            calls++;
            entries.put(key, data);
        }

        @Override
        public String loadCache(String key) {
            calls++;
            return entries.get(key);
        }

        @Override
        public void clearCache() {
            calls++;
            clearCalls++;
            entries.clear();
        }

        @Override
        public Long getLastUpdatedMillis(String key) {
            calls++;
            return entries.containsKey(key) ? 42L : null;
        }
    }

    private static final class ThrowingCacheManager implements GbCacheManager {
        @Override
        public void saveContent(String key, String data) {
            throw new IllegalStateException("synthetic save failure");
        }

        @Override
        public String loadCache(String key) {
            throw new IllegalStateException("synthetic load failure");
        }

        @Override
        public void clearCache() {
            throw new IllegalStateException("synthetic clear failure");
        }

        @Override
        public Long getLastUpdatedMillis(String key) {
            throw new IllegalStateException("synthetic timestamp failure");
        }
    }
}
