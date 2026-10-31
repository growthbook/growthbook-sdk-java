package growthbook.sdk.java.cache.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractRedisGbCacheManagerTest {

    @Test
    @DisplayName("A plain prefix is left unchanged")
    void escapeGlobLeavesPlainPrefixUnchanged() {
        // When
        String escaped = AbstractRedisGbCacheManager.escapeGlob("growthbook:features:");

        // Then
        assertEquals("growthbook:features:", escaped);
    }

    @Test
    @DisplayName("Redis glob metacharacters are escaped so clearCache can't cross namespaces")
    void escapeGlobEscapesMetacharacters() {
        // When
        String bracketed = AbstractRedisGbCacheManager.escapeGlob("user[123]:features:");
        String wildcards = AbstractRedisGbCacheManager.escapeGlob("a*b?c\\d");

        // Then
        assertEquals("user\\[123\\]:features:", bracketed);
        assertEquals("a\\*b\\?c\\\\d", wildcards);
    }
}
