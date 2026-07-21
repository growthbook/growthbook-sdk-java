package growthbook.sdk.java.cache.caffeine;

/**
 * Immutable snapshot of {@link CaffeineGbCacheManager} cache statistics.
 *
 * <p>Values are meaningful only when statistics recording is enabled via
 * {@link CaffeineCacheOptions.Builder#recordStats(boolean)}; otherwise every counter is {@code 0}.
 * This type intentionally does not expose the underlying Caffeine {@code CacheStats}, so callers
 * (for example the SDK diagnostics layer) can read cache health without a Caffeine dependency.
 */
public final class CaffeineCacheStats {

    private final long hitCount;
    private final long missCount;
    private final boolean recording;
    private final long evictionCount;

    CaffeineCacheStats(boolean recording, long hitCount, long missCount, long evictionCount) {
        this.recording = recording;
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.evictionCount = evictionCount;
    }

    /**
     * @return whether statistics recording is enabled; when {@code false} all counters are {@code 0}
     */
    public boolean isRecording() {
        return recording;
    }

    /**
     * @return number of cache lookups that returned a stored value
     */
    public long getHitCount() {
        return hitCount;
    }

    /**
     * @return number of cache lookups that did not return a stored value
     */
    public long getMissCount() {
        return missCount;
    }

    /**
     * @return number of cache lookups recorded ({@code hitCount + missCount})
     */
    public long getRequestCount() {
        return hitCount + missCount;
    }

    /**
     * @return ratio of hits to lookups in {@code [0.0, 1.0]}, or {@code 1.0} when no lookups occurred
     */
    public double getHitRate() {
        long requestCount = getRequestCount();
        return requestCount == 0 ? 1.0 : (double) hitCount / requestCount;
    }

    /**
     * @return number of entries evicted (by size or expiry)
     */
    public long getEvictionCount() {
        return evictionCount;
    }

    @Override
    public String toString() {
        return "CaffeineCacheStats{"
                + "recording=" + recording
                + ", hitCount=" + hitCount
                + ", missCount=" + missCount
                + ", evictionCount=" + evictionCount
                + '}';
    }
}
