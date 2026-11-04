package growthbook.sdk.java.cache.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractRedisGbCacheManagerTest {

    @Test
    @DisplayName("Verify: a plain prefix is left unchanged")
    void escapeGlobLeavesPlainPrefixUnchanged() {
        assertEquals("growthbook:features:", AbstractRedisGbCacheManager.escapeGlob("growthbook:features:"));
    }

    @Test
    @DisplayName("Verify: Redis glob metacharacters are escaped so clearCache can't cross namespaces")
    void escapeGlobEscapesMetacharacters() {
        assertEquals("user\\[123\\]:features:", AbstractRedisGbCacheManager.escapeGlob("user[123]:features:"));
        assertEquals("a\\*b\\?c\\\\d", AbstractRedisGbCacheManager.escapeGlob("a*b?c\\d"));
    }
}
