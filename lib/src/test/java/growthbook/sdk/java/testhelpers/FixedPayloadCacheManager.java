package growthbook.sdk.java.testhelpers;

import growthbook.sdk.java.sandbox.GbCacheManager;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link GbCacheManager} that answers every key with one fixed payload and last-updated
 * timestamp, counting loads and saves. Thread-safe.
 */
public final class FixedPayloadCacheManager implements GbCacheManager {

    private final String payload;
    @Nullable
    private final Long lastUpdatedMillis;
    private final AtomicInteger loadCount = new AtomicInteger();
    private final AtomicInteger saveCount = new AtomicInteger();

    public FixedPayloadCacheManager(String payload, @Nullable Long lastUpdatedMillis) {
        this.payload = payload;
        this.lastUpdatedMillis = lastUpdatedMillis;
    }

    public FixedPayloadCacheManager(String payload) {
        this(payload, null);
    }

    @Override
    public void saveContent(String key, String data) {
        saveCount.incrementAndGet();
    }

    @Override
    public String loadCache(String key) {
        loadCount.incrementAndGet();
        return payload;
    }

    @Override
    @Nullable
    public Long getLastUpdatedMillis(String key) {
        return lastUpdatedMillis;
    }

    @Override
    public void clearCache() {
    }

    public int loadCount() {
        return loadCount.get();
    }

    public int saveCount() {
        return saveCount.get();
    }
}
