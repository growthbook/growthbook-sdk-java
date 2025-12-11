package growthbook.sdk.java.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class LruETagCache {
    private final Cache<String, String> cache;

    public LruETagCache(int maxSize) {
        this.cache = CacheBuilder.newBuilder().maximumSize(maxSize).build();
    }

    public String get(String url) {
        return cache.getIfPresent(url);
    }

    public void put(String url, String eTag) {
        if (eTag != null) {
            cache.put(url, eTag);
        } else {
            cache.invalidate(url);
        }
    }

    public void remove(String url) {
        cache.invalidate(url);
    }

    public long size() {
        return cache.size();
    }

    public void clear() {
        cache.invalidateAll();
    }
}
