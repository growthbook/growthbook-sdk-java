package growthbook.sdk.java.sandbox;

import growthbook.sdk.java.exception.FeatureCacheException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

                Files.move(tmp, cacheDir.toPath().resolve(fileName),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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

            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                StringBuilder builder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                return builder.toString().trim();
            } catch (NoSuchFileException e) {
                log.error("Error was occur because of file isn't exist, error message was - {}", e.getMessage());
                throw new FeatureCacheException("Feature cache file disappeared while reading: " + fileName, e);
            } catch (IOException e) {
                log.error("Error was occur during reading data from file, error message was - {}", e.getMessage());

                throw new FeatureCacheException("Failed to read feature cache file: " + fileName, e);
            }
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
