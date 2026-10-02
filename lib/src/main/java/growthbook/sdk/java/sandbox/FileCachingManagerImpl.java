package growthbook.sdk.java.sandbox;

import growthbook.sdk.java.exception.FeatureCacheException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Class responsible for caching data to a file
 */
@Slf4j
public class FileCachingManagerImpl implements GbCacheManager {
    private final File cacheDir;

    /**
     * Per-instance lock guarding cache file operations. Using an instance lock (rather than a
     * class-level lock) means cache managers for different directories do not contend with one
     * another, while operations within a single cache directory remain serialized.
     */
    private final Object lock = new Object();

    public FileCachingManagerImpl(String filePath) {
        this.cacheDir = new File(filePath);
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (!created) {
                throw new FeatureCacheException("Failed to create cache directory at " + filePath);
            }
        }
        if (!cacheDir.canWrite()) {
            throw new FeatureCacheException("Cache directory is not writable: " + filePath);
        }
    }

    /**
     * Method that saves feature JSON as String to a cache file
     *
     * @param fileName The name of file in the cache directory
     * @param content  Feature JSON as String type
     */
    public void saveContent(String fileName, String content) {
        synchronized (lock) {
            Path tmp = null;
            try {
                File dest = cacheDir.toPath().resolve(fileName).toFile();
                if (dest.exists() && !dest.canWrite()) {
                    throw new IOException("File is not writable: " + dest.getAbsolutePath());
                }

                tmp = Files.createTempFile(cacheDir.toPath(), fileName, ".tmp");

                try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    writer.write(content);
                }

                moveIntoPlace(tmp, cacheDir.toPath().resolve(fileName), fileName);
                // Move consumed the temp file; nothing to clean up.
                tmp = null;

            } catch (IOException e) {
                log.error("Error occur while writing data to file with name: {} error message was {}",
                        fileName, e.getMessage());
                throw new FeatureCacheException("Failed to write feature cache file: " + fileName, e);
            } finally {
                // Clean up the temp file if the atomic move never consumed it (write or move failed),
                // otherwise stale *.tmp files accumulate in the cache directory.
                if (tmp != null) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException cleanupError) {
                        log.warn("Failed to delete temporary cache file {}", tmp, cleanupError);
                    }
                }
            }
        }
    }

    /**
     * Replaces {@code destination} with {@code source}, atomically when the filesystem supports it.
     * Some filesystems (certain network shares and container mounts) reject {@code ATOMIC_MOVE};
     * failing the whole save there would silently disable caching, because repository callers
     * suppress cache exceptions. A plain replace is used instead: the window in which a reader can
     * observe a partially written file is small, and losing the cache entirely is worse.
     */
    private void moveIntoPlace(Path source, Path destination, String fileName) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            log.debug("Atomic move is not supported for cache file {}; falling back to a non-atomic replace", fileName);
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Method that fetches data from cache by file name
     *
     * @param fileName The name of the file in the cache directory.
     * @return The cached data as a String.
     */
    public String loadCache(String fileName) {
        synchronized (lock) {
            File file = new File(cacheDir, fileName);

            if (!file.exists()) {
                return null;
            }

            try {
                return read(file.toPath(), StandardCharsets.UTF_8);
            } catch (CharacterCodingException e) {
                // Caches written by earlier SDK versions used the platform default charset, so a
                // file holding non-ASCII text may not decode as UTF-8. Retry with that charset
                // instead of discarding an otherwise usable cache; the next save rewrites it as UTF-8.
                return loadLegacyEncodedCache(file, fileName, e);
            } catch (NoSuchFileException e) {
                log.error("Error was occur because of file isn't exist, error message was - {}", e.getMessage());
                throw new FeatureCacheException("Feature cache file disappeared while reading: " + fileName, e);
            } catch (IOException e) {
                log.error("Error was occur during reading data from file, error message was - {}", e.getMessage());

                throw new FeatureCacheException("Failed to read feature cache file: " + fileName, e);
            }
        }
    }

    /**
     * Second read attempt for a cache file that is not valid UTF-8. Returns {@code null} (a cache
     * miss) when the content cannot be decoded at all, so startup falls back to fetching rather
     * than failing outright.
     */
    private String loadLegacyEncodedCache(File file, String fileName, CharacterCodingException utf8Failure) {
        Charset fallback = Charset.defaultCharset();
        if (StandardCharsets.UTF_8.equals(fallback)) {
            log.warn("Cache file {} is not valid UTF-8 and the platform default charset is UTF-8; "
                    + "treating it as a cache miss", fileName, utf8Failure);
            return null;
        }

        try {
            String content = read(file.toPath(), fallback);
            log.warn("Cache file {} was not valid UTF-8; read it using the platform default charset ({}). "
                    + "It will be rewritten as UTF-8 on the next save.", fileName, fallback);
            return content;
        } catch (IOException e) {
            log.warn("Cache file {} could not be decoded as UTF-8 or {}; treating it as a cache miss",
                    fileName, fallback, e);
            return null;
        }
    }

    private String read(Path path, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            return builder.toString().trim();
        }
    }

    @Override
    public Long getLastUpdatedMillis(String fileName) {
        synchronized (lock) {
            Path cacheFile = new File(cacheDir, fileName).toPath();
            try {
                long lastModified = Files.getLastModifiedTime(cacheFile).toMillis();
                return lastModified > 0 ? lastModified : null;
            } catch (NoSuchFileException e) {
                return null;
            } catch (IOException e) {
                throw new FeatureCacheException("Failed to read last modified time for cache file: " + fileName, e);
            }
        }
    }

    /**
     * Clears all cache files in the directory
     */
    public void clearCache() {
        synchronized (lock) {
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        try {
                            Files.delete(file.toPath());
                        } catch (IOException e) {
                            log.error("Failed to delete cache file: {}", file.getName(), e);
                        }
                    }
                }
            }
        }
    }
}
