package growthbook.sdk.java.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.internal.FeatureRefreshNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class LocalGbFeatureRepository implements IGBFeaturesRepository {

    private static final String PATH_DELIMITER = "/";

    private String featuresJson = "{}";
    private final FeatureRefreshNotifier featureRefreshNotifier = new FeatureRefreshNotifier(this::getActiveFeatureCount, () -> null);

    /**
     * Pass path from source root
     */
    private final String jsonPath;

    /**
     * Method for initializing {@link LocalGbFeatureRepository} by fetching features from user's json file
     *
     * @throws FeatureFetchException exception when FileNotFoundException occur
     */
    @Override
    public void initialize() throws FeatureFetchException {
        long startedAtNanos = System.nanoTime();
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();

        String resourcesDirectory = getResourceDirectoryPath();
        String fullPath = resourcesDirectory + validateUserJsonPath(jsonPath);

        try (FileReader reader = new FileReader(fullPath)) {
            JsonObject featuresJsonObject = gson.fromJson(reader, JsonObject.class);
            String refreshedFeatures = featuresJsonObject.toString();
            boolean featuresChanged = !Objects.equals(this.featuresJson, refreshedFeatures);
            this.featuresJson = refreshedFeatures;
            log.info("LocalGbFeatureRepository load features from {} successfully", fullPath);
            log.info("Features: {}", featuresJson);
            featureRefreshNotifier.notifySuccess(
                    FeatureRefreshSource.INITIALIZATION,
                    featuresChanged,
                    false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
            );
        } catch (IOException e) {
            log.error("LocalGbFeatureRepository cannot load features from {}, Exception was: {}",
                    fullPath,
                    e.getMessage(),
                    e);
            FeatureFetchException featureFetchException = new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "Failed to load features from: " + fullPath);
            featureRefreshNotifier.notifyFailure(
                    featureFetchException,
                    FeatureRefreshSource.INITIALIZATION,
                    false,
                    false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
            );
            throw featureFetchException;
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
     *
     * @return featuresJson
     */
    @Override
    public String getFeaturesJson() {
        return this.featuresJson;
    }

    public Map<String, Feature<?>> getParsedFeatures() {
        return TransformationUtil.transformFeatures(this.featuresJson);
    }

    /**
     * Can be ignored for this implementation
     *
     * @deprecated Use {@link #addFeatureRefreshListener(FeatureRefreshListener)}.
     */
    @Deprecated
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        // Not needed in this implementation
    }

    @Override
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        if (listener != null) {
            this.featureRefreshNotifier.add(listener);
        }
    }

    @Override
    public void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        if (listener != null) {
            this.featureRefreshNotifier.remove(listener);
        }
    }

    /**
     * Can be ignored for this implementation
     *
     * @deprecated Use listener-specific unsubscription with
     * {@link #removeFeatureRefreshListener(FeatureRefreshListener)} where available.
     */
    @Deprecated
    @Override
    public void clearCallbacks() {
        // Not needed in this implementation
    }

    private String getResourceDirectoryPath() {
        Path resourceDirectory = Paths.get("src", "main", "resources");
        return resourceDirectory.toFile().getAbsolutePath();
    }

    private String validateUserJsonPath(String jsonPath) {
        if (!jsonPath.startsWith(PATH_DELIMITER)) {
            jsonPath = PATH_DELIMITER + jsonPath;
        }
        if (!jsonPath.endsWith(".json")) {
            jsonPath += ".json";
        }
        return jsonPath;
    }

    private int getActiveFeatureCount() {
        Map<String, Feature<?>> features = TransformationUtil.transformFeatures(this.featuresJson);
        return features == null ? 0 : features.size();
    }
}
