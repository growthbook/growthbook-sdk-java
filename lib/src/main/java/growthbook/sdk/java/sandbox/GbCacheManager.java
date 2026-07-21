package growthbook.sdk.java.sandbox;

public interface GbCacheManager {
    void saveContent(String key, String data);
    String loadCache(String key);
    void clearCache();

    /**
     * Returns when the cached value was last updated, in epoch milliseconds.
     *
     * <p>The optional background feature refresh interval uses this timestamp
     * to decide whether cached features are fresh enough to skip a network
     * refresh. Custom cache implementations may return {@code null} when
     * timestamp tracking is unsupported or when the key does not exist; in that
     * case the SDK treats cache freshness as unknown and performs the network
     * refresh. Implementations should throw
     * {@link growthbook.sdk.java.exception.FeatureCacheException} for real cache
     * access failures.
     */
    default Long getLastUpdatedMillis(String key) {
        return null;
    }
}
