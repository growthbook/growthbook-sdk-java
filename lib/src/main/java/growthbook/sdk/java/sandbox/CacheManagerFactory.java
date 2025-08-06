package growthbook.sdk.java.sandbox;

import javax.annotation.Nullable;

public class CacheManagerFactory {
    public static GbCacheManager createCacheManager(@Nullable String cachePath, boolean inMemoryOnly) {
        if (inMemoryOnly) {
            return new InMemoryCachingManagerImpl();
        }
        return new FileCachingManagerImpl(cachePath);
    }
}