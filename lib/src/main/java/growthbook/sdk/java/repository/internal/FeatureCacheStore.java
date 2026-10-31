package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The persisted copy of the last good features payload, under one cache key. The
 * {@link GbCacheManager} is user-supplied and untrusted: its failures are logged and treated as
 * a cache miss, never as a failed refresh. Thread-safe.
 */
@Slf4j
public final class FeatureCacheStore {

    private final String cacheKey;
    @Getter
    private final boolean disabled;
    private final AtomicReference<GbCacheManager> manager = new AtomicReference<>();

    /**
     * @param cacheKey the key the payload is stored under
     * @param disabled whether caching is turned off; the store then never reads or writes
     * @param manager  the cache manager, or {@code null} for the default one
     */
    public FeatureCacheStore(String cacheKey, boolean disabled, @Nullable GbCacheManager manager) {
        this.cacheKey = cacheKey;
        this.disabled = disabled;
        if (!disabled) {
            this.manager.set(manager != null ? manager : createDefault());
        }
    }

    @Nullable
    public GbCacheManager manager() {
        return manager.get();
    }

    /**
     * Replaces the cache manager. Ignored, with a warning, when caching is disabled.
     */
    public void setManager(GbCacheManager cacheManager) {
        if (disabled) {
            log.warn("Cache is disabled. Please enable it and set the CacheManager");
            return;
        }
        manager.set(cacheManager);
    }

    /**
     * Drops the manager reference without clearing the store: persisted features are meant to
     * survive restarts, and a shared store (e.g. Redis) also serves other SDK instances.
     */
    public void release() {
        if (manager.getAndSet(null) != null) {
            log.info("CacheManager released");
        }
    }

    /**
     * @return when the payload was last written, or {@code null} when unknown
     */
    @Nullable
    public Long lastUpdatedMillis() {
        GbCacheManager current = activeManager();
        if (current == null) {
            return null;
        }
        try {
            return current.getLastUpdatedMillis(cacheKey);
        } catch (RuntimeException e) {
            log.warn("Failed to read the feature cache timestamp.", e);
            return null;
        }
    }

    /**
     * @return the cached payload, or {@code null} when there is none or it is blank
     */
    @Nullable
    public String load() {
        GbCacheManager current = activeManager();
        if (current == null) {
            return null;
        }
        try {
            String cached = current.loadCache(cacheKey);
            return cached == null || cached.trim().isEmpty() ? null : cached;
        } catch (RuntimeException e) {
            log.warn("Failed to load cached features.", e);
            return null;
        }
    }

    /**
     * Stores a payload, ignoring cache failures so they never disrupt a refresh.
     */
    public void saveQuietly(String payload) {
        GbCacheManager current = activeManager();
        if (current == null) {
            return;
        }
        try {
            current.saveContent(cacheKey, payload);
        } catch (RuntimeException e) {
            log.debug("Failed to save features to the cache.", e);
        }
    }

    @Nullable
    private GbCacheManager activeManager() {
        return disabled ? null : manager.get();
    }

    @Nullable
    private static GbCacheManager createDefault() {
        try {
            return CacheManagerFactory.create(CacheMode.AUTO, null);
        } catch (Exception e) {
            log.warn("Cache Manager creation failed", e);
            return null;
        }
    }
}
