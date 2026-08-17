package growthbook.sdk.java.sandbox;

import growthbook.sdk.java.exception.FeatureCacheException;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * File-based {@link GbCacheManager}: stores one file per key inside a cache directory.
 *
 * <p>Writes are <b>atomic</b>: content is written to a temporary file and then renamed
 * over the target ({@link StandardCopyOption#ATOMIC_MOVE}, falling back to a plain replace
 * when the filesystem does not support atomic moves). This guarantees a reader never sees a
 * half-written file and a crash mid-write cannot leave a corrupted cache entry behind — the
 * previous value simply remains in place.
 *
 * <p>All I/O uses UTF-8 explicitly so content round-trips regardless of the platform default
 * charset. This class performs no cross-key locking; callers that need per-key mutual
 * exclusion (e.g. read-modify-write) must synchronize externally.
 */
@Slf4j
public class FileCachingManagerImpl implements GbCacheManager {

    private final Path cacheDir;

    public FileCachingManagerImpl(@Nullable String filePath) {
        Path dir = (filePath == null)
                ? Paths.get(System.getProperty("java.io.tmpdir"), "growthbook_cache")
                : Paths.get(filePath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new FeatureCacheException("Failed to create cache directory at " + dir, e);
        }
        if (!Files.isWritable(dir)) {
            throw new FeatureCacheException("Cache directory is not writable: " + dir);
        }
        this.cacheDir = dir;
    }

    /**
     * Atomically saves {@code content} under {@code fileName} in the cache directory.
     *
     * @param fileName the file name (must be a plain name, without path separators)
     * @param content  the content to persist
     */
    @Override
    public void saveContent(String fileName, String content) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(cacheDir, fileName + ".", ".tmp");
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            moveAtomically(tmp, cacheDir.resolve(fileName));
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new FeatureCacheException("Failed to write cache file: " + fileName, e);
        }
    }

    /**
     * Loads the content stored under {@code fileName}.
     *
     * @param fileName the file name in the cache directory
     * @return the cached content, or {@code null} if there is no such file
     */
    @Override
    public String loadCache(String fileName) {
        Path file = cacheDir.resolve(fileName);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new FeatureCacheException("Failed to read cache file: " + fileName, e);
        }
    }

    /**
     * Removes every file in the cache directory.
     */
    @Override
    public void clearCache() {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(cacheDir)) {
            entries.forEach(FileCachingManagerImpl::deleteQuietly);
        } catch (IOException e) {
            throw new FeatureCacheException("Failed to clear cache directory: " + cacheDir, e);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(@Nullable Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete cache file {}: {}", path, e.getMessage());
        }
    }
}
