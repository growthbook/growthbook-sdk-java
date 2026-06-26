package growthbook.sdk.java;

import growthbook.sdk.java.sandbox.InMemoryCachingManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryCachingManagerImplTest {

    private InMemoryCachingManagerImpl cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCachingManagerImpl();
    }

    @Test
    void shouldReturnNullForMissingKey() {
        assertNull(cache.loadCache("missing"));
    }

    @Test
    void shouldSaveAndLoadValue() {
        cache.saveContent("key1", "value1");
        assertEquals("value1", cache.loadCache("key1"));
    }

    @Test
    void shouldOverwriteExistingKey() {
        cache.saveContent("key", "first");
        cache.saveContent("key", "second");
        assertEquals("second", cache.loadCache("key"));
    }

    @Test
    void shouldStoreMultipleKeysIndependently() {
        cache.saveContent("a", "alpha");
        cache.saveContent("b", "beta");

        assertEquals("alpha", cache.loadCache("a"));
        assertEquals("beta", cache.loadCache("b"));
    }

    @Test
    void shouldClearAllEntries() {
        cache.saveContent("x", "1");
        cache.saveContent("y", "2");

        cache.clearCache();

        assertNull(cache.loadCache("x"));
        assertNull(cache.loadCache("y"));
    }

    @Test
    void shouldSaveEmptyStringValue() {
        cache.saveContent("empty", "");
        assertEquals("", cache.loadCache("empty"));
    }
}
