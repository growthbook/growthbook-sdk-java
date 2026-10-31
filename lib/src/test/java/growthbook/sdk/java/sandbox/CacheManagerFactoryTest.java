package growthbook.sdk.java;

import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.InMemoryCachingManagerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerFactoryTest {

    @TempDir
    File tempDir;

    @Test
    void noneMode_returnsNull() {
        GbCacheManager result = CacheManagerFactory.create(CacheMode.NONE, null);
        assertNull(result);
    }

    @Test
    void memoryMode_returnsInMemoryManager() {
        GbCacheManager result = CacheManagerFactory.create(CacheMode.MEMORY, null);
        assertInstanceOf(InMemoryCachingManagerImpl.class, result);
    }

    @Test
    void memoryMode_explicitDirIsIgnored() {
        GbCacheManager result = CacheManagerFactory.create(CacheMode.MEMORY, tempDir.getAbsolutePath());
        assertInstanceOf(InMemoryCachingManagerImpl.class, result);
    }

    @Test
    void fileMode_withWritableDir_returnsFileCachingManager() {
        GbCacheManager result = CacheManagerFactory.create(CacheMode.FILE, tempDir.getAbsolutePath());
        assertInstanceOf(FileCachingManagerImpl.class, result);
    }

    @Test
    void fileMode_withUnusableDir_returnsNonNullManager() {
        // A path rooted under a plain file cannot be used as a cache directory.
        // The factory will try system fallback locations (tmpdir, XDG, ~/Library/Caches…)
        // before falling back to InMemoryCachingManagerImpl — so we only assert non-null.
        File plainFile = new File(tempDir, "notadir.txt");
        try { plainFile.createNewFile(); } catch (Exception ignored) {}
        String impossiblePath = plainFile.getAbsolutePath() + File.separator + "sub";

        GbCacheManager result = CacheManagerFactory.create(CacheMode.FILE, impossiblePath);
        assertNotNull(result);
    }

    @Test
    void fileMode_withNullDir_usesSystemFallbackOrMemory() {
        // Should never throw; returns either File or Memory manager.
        GbCacheManager result = CacheManagerFactory.create(CacheMode.FILE, null);
        assertNotNull(result);
        assertTrue(result instanceof FileCachingManagerImpl || result instanceof InMemoryCachingManagerImpl);
    }

    @Test
    void autoMode_withWritableDir_returnsFileCachingManager() {
        GbCacheManager result = CacheManagerFactory.create(CacheMode.AUTO, tempDir.getAbsolutePath());
        assertInstanceOf(FileCachingManagerImpl.class, result);
    }

    @Test
    void autoMode_withUnusableDir_returnsNonNullManager() {
        File plainFile = new File(tempDir, "notadir2.txt");
        try { plainFile.createNewFile(); } catch (Exception ignored) {}
        String impossiblePath = plainFile.getAbsolutePath() + File.separator + "sub";

        // The factory tries multiple system fallback locations before giving up.
        GbCacheManager result = CacheManagerFactory.create(CacheMode.AUTO, impossiblePath);
        assertNotNull(result);
    }

    @Test
    void autoMode_withNullDir_returnsNonNullManager() {
        GbCacheManager result = CacheManagerFactory.create(CacheMode.AUTO, null);
        assertNotNull(result);
    }

    @Test
    void fileManager_createdByFactory_canSaveAndLoad() {
        GbCacheManager manager = CacheManagerFactory.create(CacheMode.FILE, tempDir.getAbsolutePath());
        assertInstanceOf(FileCachingManagerImpl.class, manager);

        manager.saveContent("smoke.json", "{\"flag\":true}");
        assertEquals("{\"flag\":true}", manager.loadCache("smoke.json"));
    }

    @Test
    void memoryManager_createdByFactory_canSaveAndLoad() {
        GbCacheManager manager = CacheManagerFactory.create(CacheMode.MEMORY, null);

        assertNotNull(manager);
        manager.saveContent("key", "value");
        assertEquals("value", manager.loadCache("key"));
    }
}
