package growthbook.sdk.java.repository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ETags by URL, holding at most {@code maxSize} entries and evicting the least recently used.
 * Thread-safe.
 */
public class LruETagCache {

    private final Map<String, String> cache;

    /**
     * @param maxSize the most entries kept; zero keeps none
     * @throws IllegalArgumentException when {@code maxSize} is negative
     */
    public LruETagCache(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must not be negative: " + maxSize);
        }
        this.cache = new LinkedHashMap<String, String>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized String get(String url) {
        return cache.get(url);
    }

    public synchronized void put(String url, String eTag) {
        if (eTag != null) {
            cache.put(url, eTag);
        } else {
            cache.remove(url);
        }
    }

    public synchronized void remove(String url) {
        cache.remove(url);
    }

    public synchronized long size() {
        return cache.size();
    }

    public synchronized void clear() {
        cache.clear();
    }
}
