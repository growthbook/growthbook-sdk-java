package growthbook.sdk.java.cache.jcache;

import java.io.Serializable;

/**
 * Cached payload plus the epoch-millis timestamp of when it was written.
 *
 * <p>Implements {@link Serializable} because JSR-107 caches default to store-by-value semantics,
 * where the provider copies values via serialization.
 */
final class JCacheCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String data;
    private final long lastUpdatedMillis;

    JCacheCacheEntry(String data, long lastUpdatedMillis) {
        this.data = data;
        this.lastUpdatedMillis = lastUpdatedMillis;
    }

    String getData() {
        return data;
    }

    long getLastUpdatedMillis() {
        return lastUpdatedMillis;
    }
}
