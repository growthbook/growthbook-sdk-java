package growthbook.sdk.java.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RequiredArgsConstructor
public class LocalGbFeatureRepository implements IGBFeaturesRepository {
    private String featuresJson = "{}";
    /**
     * Pass path from source root
     */
    private final String jsonPath;

    /**
     * Method for initializing {@link LocalGbFeatureRepository} by fetching features from user's json file
     * @throws FeatureFetchException exception when FileNotFoundException occur
     */
    @Override
    public void initialize() throws FeatureFetchException {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();

        String resourcesDirectory = getResourceDirectoryPath();
        String fullPath = resourcesDirectory + validateUserJsonPath(jsonPath);

        try {
            JsonObject featuresJsonObject = gson.fromJson(
                    new FileReader(fullPath),
                    JsonObject.class
            );
            this.featuresJson = featuresJsonObject.toString();
            log.info("LocalGbFeatureRepository load features from {} successfully", fullPath);
            log.info("Features: {}", featuresJson);
        } catch (FileNotFoundException e) {
            log.error("LocalGbFeatureRepository cannot load features from {}, Exception was: {}",
                    fullPath,
                    e.getMessage(),
                    e);
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "Failed to load features from: " + fullPath);
        }

    }

    /**
     * Can be ignored for this implementation
     */
    @Override
    public void initialize(Boolean retryOnFailure) {
        // Not needed in this implementation
    }


    /**
     * Method for getting the featuresJson in format of String from user json file
     * @return featuresJson
     */
    @Override
    public String getFeaturesJson() {
        return this.featuresJson;
    }

    /**
     * Can be ignored for this implementation
     */
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        // Not needed in this implementation
    }

    /**
     * Can be ignored for this implementation
     */
    @Override
    public void clearCallbacks() {
        // Not needed in this implementation
    }

    private String getResourceDirectoryPath() {
        Path resourceDirectory = Paths.get("src", "main", "resources");
        return resourceDirectory.toFile().getAbsolutePath();
    }

    private String validateUserJsonPath(String jsonPath) {
        if (!jsonPath.startsWith("/")) {
            jsonPath = "/" + jsonPath;
        }
        if (!jsonPath.endsWith(".json")) {
            jsonPath += ".json";
        }
        return jsonPath;
    }
}
