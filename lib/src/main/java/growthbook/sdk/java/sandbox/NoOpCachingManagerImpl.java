package growthbook.sdk.java.sandbox;

/**
 * Cache manager that performs no operations. Used when caching is disabled.
 */
public class NoOpCachingManagerImpl implements GbCacheManager {
    @Override
    public void saveContent(String key, String data) {
        // no-op
    }

    @Override
    public String loadCache(String key) {
        return null;
    }

    @Override
    public void clearCache() {
        // no-op
    }
}


