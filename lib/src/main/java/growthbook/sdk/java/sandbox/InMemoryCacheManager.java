package growthbook.sdk.java.sandbox;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCacheManager implements GbCacheManager{
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public void saveContent(String key, String data) {
        cache.put(key, data);
    }

    @Override
    public String loadCache(String key) {
        return cache.get(key);
    }

    @Override
    public void clearCache() {
        cache.clear();
    }
}
