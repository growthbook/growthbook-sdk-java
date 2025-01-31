package growthbook.sdk.java;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * Class responsible for caching data to a file
 */
@Slf4j
public class CachingManager {
    private final File cacheDir;

    public CachingManager(String filePath) {
        this.cacheDir = new File(filePath);
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create cache directory at " + filePath);
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
                builder.append(line).append(System.lineSeparator());
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
}
