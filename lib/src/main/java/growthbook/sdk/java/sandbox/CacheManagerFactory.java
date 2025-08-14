package growthbook.sdk.java.sandbox;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class CacheManagerFactory {

    public static GbCacheManager create(CacheMode mode, String explicitCacheDirOrNull) {
        if (mode == CacheMode.NONE) {
            return null; // no cache manager
        }

        if (mode == CacheMode.MEMORY) {
            // Simple in-memory store
            return new InMemoryCachingManagerImpl();
        }

        if (mode == CacheMode.FILE) {
            FileCachingManagerImpl fileManager = tryCreateFileManager(explicitCacheDirOrNull);
            if (fileManager != null) return fileManager;
            log.warn("CacheMode.FILE requested but directory is not usable. Falling back to MEMORY.");
            return new InMemoryCachingManagerImpl();
        }

        // AUTO
        FileCachingManagerImpl fileManager = tryCreateFileManager(explicitCacheDirOrNull);
        if (fileManager != null) return fileManager;
        return new InMemoryCachingManagerImpl();
    }

    private static FileCachingManagerImpl tryCreateFileManager(String explicitDir) {
        String resolved = resolveWritableDir(explicitDir);
        if (resolved == null) return null;
        try {
            return new FileCachingManagerImpl(resolved);
        } catch (RuntimeException ex) {
            log.warn("Failed to initialize file cache at {}: {}", resolved, ex.getMessage());
            return null;
        }
    }

    private static String resolveWritableDir(String explicitDir) {
        if (isWritableDir(explicitDir)) return explicitDir;

        String fromSysProp = System.getProperty("growthbook.cache.dir");
        if (isWritableDir(fromSysProp)) return fromSysProp;

        // XDG cache
        String xdg = System.getenv("XDG_CACHE_HOME");
        if (xdg != null) {
            String p = Paths.get(xdg, "growthbook").toString();
            if (isWritableDir(p)) return p;
        }

        // macOS ~/Library/Caches/growthbook
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            String mac = Paths.get(userHome, "Library", "Caches", "growthbook").toString();
            if (isWritableDir(mac)) return mac;
        }

        // Windows %LOCALAPPDATA%/GrowthBook
        String localApp = System.getenv("LOCALAPPDATA");
        if (localApp != null) {
            String win = Paths.get(localApp, "GrowthBook").toString();
            if (isWritableDir(win)) return win;
        }

        // Fallback: java.io.tmpdir/growthbook-cache
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) {
            String p = Paths.get(tmp, "growthbook-cache").toString();
            if (isWritableDir(p)) return p;
        }

        return null;
    }

    private static boolean isWritableDir(String dir) {
        if (dir == null || dir.isEmpty()) return false;
        Path path = Paths.get(dir);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return Files.isDirectory(path) && Files.isWritable(path);
        } catch (Exception e) {
            return false;
        }
    }
}


