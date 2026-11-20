package growthbook.sdk.java.testhelpers;

import growthbook.sdk.java.sandbox.GbCacheManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link GbCacheManager} over a map, recording how it was used. Thread-safe.
 */
public final class InMemoryCacheManager implements GbCacheManager {

    public final Map<String, String> store = new ConcurrentHashMap<>();
    private final AtomicInteger saveCount = new AtomicInteger();
    private volatile boolean cleared;

    @Override
    public void saveContent(String key, String data) {
        saveCount.incrementAndGet();
        store.put(key, data);
    }

    @Override
    public String loadCache(String key) {
        return store.get(key);
    }

    @Override
    public void clearCache() {
        cleared = true;
        store.clear();
    }

    public int saveCount() {
        return saveCount.get();
    }

    public boolean cleared() {
        return cleared;
    }
}
