package growthbook.sdk.java.sandbox;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FileCachingManagerImplContractTest extends GbCacheManagerContractTest {

    @TempDir
    Path cacheDirectory;

    @Override
    protected GbCacheManager createCacheManager() {
        return new FileCachingManagerImpl(cacheDirectory.toString());
    }

    @Override
    protected long timestampToleranceMillis() {
        return 1000L;
    }
}
