package growthbook.sdk.java.sandbox;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.Files;

/**
 * Class responsible for caching data to a file
 */
@Slf4j
public class FileCachingManagerImpl implements GbCacheManager {
    private final File cacheDir;

    public FileCachingManagerImpl(@Nullable String filePath) {
        if (filePath == null) {
            this.cacheDir = new File(System.getProperty("java.io.tmpdir"), "growthbook_cache");
        } else {
            this.cacheDir = new File(filePath);
        }
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (!created) {
                log.warn("Failed to create cache directory at {}", cacheDir.getAbsolutePath());
                throw new RuntimeException("Failed to create cache directory at " +  cacheDir.getAbsolutePath());
            }
        }
    }

    /**
     * Method that saves feature JSON as String to a cache file
     * @param fileName The name of file in the cache directory
     * @param content Feature JSON as String type
     */
    public void saveContent(String fileName, String content) {
        File file = new File(cacheDir, fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
           writer.write(content);
       } catch (IOException e) {
           log.error("Error occur while writing data to file with name: {} error message was {}", fileName, e.getMessage());
           throw new RuntimeException(e);
       }
    }

    /**
     * Method that fetches data from cache by file name
     * @param fileName The name of the file in the cache directory.
     * @return The cached data as a String.
     */
    public String loadCache(String fileName) {
        File file = new File(cacheDir, fileName);

        if (!file.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            return builder.toString().trim();
        } catch (FileNotFoundException e) {
            log.error("Error was occur because of file isn't exist, error message was - {}", e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("Error was occur during reading data from file, error message was - {}", e.getMessage());

            throw new RuntimeException(e);
        }
    }

    /**
     * Clears all cache files in the directory
     */
    public void clearCache(){
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
