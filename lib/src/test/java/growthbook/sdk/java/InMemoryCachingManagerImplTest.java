package growthbook.sdk.java;

import growthbook.sdk.java.sandbox.InMemoryCachingManagerImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryCachingManagerImplTest {
    @Test
    void tracksAndClearsLastUpdatedTime() {
        InMemoryCachingManagerImpl cacheManager = new InMemoryCachingManagerImpl();

        cacheManager.saveContent("features", "{}");

        assertNotNull(cacheManager.getLastUpdatedMillis("features"));

        cacheManager.clearCache();

        assertNull(cacheManager.getLastUpdatedMillis("features"));
    }
}
