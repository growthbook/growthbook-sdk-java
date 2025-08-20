package growthbook.sdk.java.sandbox;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple process-lifetime in-memory cache manager.
 */
public class InMemoryCachingManagerImpl implements GbCacheManager {
    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void saveContent(String key, String data) {
        store.put(key, data);
    }

    @Override
    public String loadCache(String key) {
        return store.get(key);
    }

    @Override
    public void clearCache() {
        store.clear();
    }
}


