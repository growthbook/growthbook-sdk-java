package growthbook.sdk.java.sandbox;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileCachingManagerImplContractTest extends GbCacheManagerContractTest {

    @TempDir
    Path cacheDirectory;

    @Override
    protected GbCacheManager createCacheManager() {
        return new FileCachingManagerImpl(cacheDirectory.toString());
    }

    @Override
    protected long expectedLastUpdatedMillis(String key) {
        try {
            return Files.getLastModifiedTime(cacheDirectory.resolve(key)).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
