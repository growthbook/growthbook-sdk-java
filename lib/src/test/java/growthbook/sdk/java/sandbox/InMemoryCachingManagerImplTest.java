package growthbook.sdk.java.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCachingManagerImplTest {

    private final InMemoryCachingManagerImpl cache = new InMemoryCachingManagerImpl();

    @Test
    @DisplayName("Returns null content and no timestamp for a key that was never saved")
    void returnsNullForMissingKey() {
        // Given
        // When
        String content = cache.loadCache("missing");
        Long updatedAt = cache.getLastUpdatedMillis("missing");

        // Then
        assertNull(content);
        assertNull(updatedAt);
    }

    @ParameterizedTest(name = "content \"{0}\"")
    @ValueSource(strings = {"value-1", ""})
    @DisplayName("Loads exactly the content that was saved")
    void loadsSavedContent(String content) {
        // Given
        cache.saveContent("key-1", content);

        // When
        String loaded = cache.loadCache("key-1");

        // Then
        assertEquals(content, loaded);
    }

    @Test
    @DisplayName("Records the save time as the last updated timestamp")
    void recordsSaveTimeAsLastUpdated() {
        // Given
        long before = System.currentTimeMillis();

        // When
        cache.saveContent("key-1", "value-1");
        long after = System.currentTimeMillis();

        // Then
        long updatedAt = cache.getLastUpdatedMillis("key-1");
        assertTrue(updatedAt >= before && updatedAt <= after,
                "expected " + updatedAt + " within [" + before + ", " + after + "]");
    }

    @Test
    @DisplayName("Replaces the content of a key that is saved again")
    void replacesContentOfSavedKey() {
        // Given
        cache.saveContent("key-1", "first");

        // When
        cache.saveContent("key-1", "second");

        // Then
        assertEquals("second", cache.loadCache("key-1"));
    }

    @Test
    @DisplayName("Keeps the content of different keys independent")
    void keepsKeysIndependent() {
        // Given
        // When
        cache.saveContent("key-a", "alpha");
        cache.saveContent("key-b", "beta");

        // Then
        assertEquals("alpha", cache.loadCache("key-a"));
        assertEquals("beta", cache.loadCache("key-b"));
    }

    @Test
    @DisplayName("Clears every saved entry and its timestamp")
    void clearsEntriesAndTimestamps() {
        // Given
        cache.saveContent("key-x", "1");
        cache.saveContent("key-y", "2");

        // When
        cache.clearCache();

        // Then
        assertNull(cache.loadCache("key-x"));
        assertNull(cache.loadCache("key-y"));
        assertNull(cache.getLastUpdatedMillis("key-x"));
        assertNull(cache.getLastUpdatedMillis("key-y"));
    }
}
