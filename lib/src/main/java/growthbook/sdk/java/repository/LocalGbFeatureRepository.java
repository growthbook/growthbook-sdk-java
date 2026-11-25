package growthbook.sdk.java.repository;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.internal.FeatureRefreshNotifier;
import growthbook.sdk.java.repository.internal.RefreshCallbacks;
import growthbook.sdk.java.repository.internal.RefreshMetrics;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads features once from a JSON file under {@code src/main/resources}, for tests and offline
 * use. The file holds the features object itself. There is no refreshing: {@link #initialize()}
 * loads the file, and the features stay until it is called again.
 *
 * <p>Thread-safe: the loaded features are published as one atomic {@link FeatureSnapshot}.
 */
@Slf4j
public class LocalGbFeatureRepository implements IGBFeaturesRepository {
    private static final String PATH_DELIMITER = "/";
    private static final String JSON_EXTENSION = ".json";

    /**
     * Pass path from source root
     */
    private final String jsonPath;
    private final AtomicReference<FeatureSnapshot> snapshot = new AtomicReference<>(FeatureSnapshot.EMPTY);
    private final RefreshCallbacks callbacks = new RefreshCallbacks();
    private final RefreshMetrics metrics;
    private final FeatureRefreshNotifier notifier = new FeatureRefreshNotifier(this::getActiveFeatureCount, () -> null);

    /**
     * @param jsonPath the features file, relative to {@code src/main/resources}; a leading
     *                 {@code /} and the {@code .json} extension are optional
     */
    public LocalGbFeatureRepository(String jsonPath) {
        this(jsonPath, Clock.systemUTC());
    }

    LocalGbFeatureRepository(String jsonPath, Clock clock) {
        this.jsonPath = jsonPath;
        this.metrics = new RefreshMetrics(clock);
    }

    /**
     * Loads the features file, as UTF-8.
     *
     * @throws FeatureFetchException {@code CONFIGURATION_ERROR} when the file is missing,
     *                               unreadable, empty or not a JSON object
     */
    @Override
    public void initialize() throws FeatureFetchException {
        long startedAtNanos = System.nanoTime();
        String location = resourceDirectory() + normalized(jsonPath);
        try {
            String featuresJson = readFeatures(Paths.get(location));
            boolean featuresChanged = !Objects.equals(snapshot.get().getFeaturesJson(), featuresJson);
            Map<String, Feature<?>> features = TransformationUtil.transformFeatures(featuresJson);
            snapshot.set(FeatureSnapshot.of(
                    featuresJson,
                    "",
                    features != null ? features : Collections.<String, Feature<?>>emptyMap(),
                    new JsonObject()));
            log.info("LocalGbFeatureRepository load features from {} successfully", location);
            log.debug("Features: {}", featuresJson);
            metrics.recordFetched();
            metrics.recordSuccess(false);
            callbacks.notifyRefresh(featuresJson);
            notifier.notifySuccess(FeatureRefreshSource.INITIALIZATION, featuresChanged, false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos));
        } catch (IOException | JsonParseException | InvalidPathException e) {
            log.error("LocalGbFeatureRepository cannot load features from {}, Exception was: {}", location, e.getMessage(), e);
            FeatureFetchException failure = new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "Failed to load features from: " + location);
            metrics.recordError(failure);
            metrics.recordFailure(false);
            callbacks.notifyError(failure);
            notifier.notifyFailure(failure, FeatureRefreshSource.INITIALIZATION, false, false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos));
            throw failure;
        }
    }

    /**
     * Loads the features file like {@link #initialize()}; there is nothing to retry. A failure is
     * logged and reported to callbacks and listeners, since this overload declares no exception.
     *
     * @param retryOnFailure ignored
     */
    @Override
    public void initialize(Boolean retryOnFailure) {
        try {
            initialize();
        } catch (FeatureFetchException e) {
            log.error("LocalGbFeatureRepository failed to initialize: {}", e.getMessage());
        }
    }

    /**
     * @return the features JSON, or {@code "{}"} before the file is loaded
     */
    @Override
    public String getFeaturesJson() {
        return snapshot.get().getFeaturesJson();
    }

    /**
     * @return a freshly parsed copy of the loaded feature definitions; changing it does not affect
     * the repository or other callers. Empty when no features are loaded.
     */
    public Map<String, Feature<?>> getParsedFeatures() {
        return TransformationUtil.transformFeatures(snapshot.get().getFeaturesJson());
    }

    /**
     * @return the loaded features as one immutable snapshot
     */
    public FeatureSnapshot getFeatureSnapshot() {
        return snapshot.get();
    }

    /**
     * @deprecated Use {@link #addFeatureRefreshListener(FeatureRefreshListener)}.
     */
    @Deprecated
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        if (listener != null) {
            notifier.add(listener);
        }
    }

    @Override
    public void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        if (listener != null) {
            notifier.remove(listener);
        }
    }

    /**
     * @deprecated Use listener-specific unsubscription with
     * {@link #removeFeatureRefreshListener(FeatureRefreshListener)} where available.
     */
    @Deprecated
    @Override
    public void clearCallbacks() {
        callbacks.clear();
    }

    @Override
    @Nullable
    public Throwable getLastRefreshError() {
        return metrics.lastError();
    }

    @Override
    public long getLastRefreshErrorAtMillis() {
        return metrics.lastErrorAtMillis();
    }

    @Override
    public long getRefreshSuccessCount() {
        return metrics.successCount();
    }

    @Override
    public long getRefreshFailureCount() {
        return metrics.failureCount();
    }

    @Override
    public long getRefreshConsecutiveFailureCount() {
        return metrics.consecutiveFailures();
    }

    @Override
    public long getLastRefreshFailureAtMillis() {
        return metrics.lastFailureAtMillis();
    }

    @Override
    @Nullable
    public Boolean getLastRefreshLoadedFromCache() {
        return metrics.lastLoadedFromCache();
    }

    /**
     * @throws JsonParseException when the file is empty, {@code null} or not a JSON object
     */
    private static String readFeatures(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject features = GrowthBookJsonUtils.getInstance().gson.fromJson(reader, JsonObject.class);
            if (features == null) {
                throw new JsonParseException("Feature file is empty or contains a JSON null: " + path);
            }
            return features.toString();
        }
    }

    private static String resourceDirectory() {
        return Paths.get("src", "main", "resources").toFile().getAbsolutePath();
    }

    private static String normalized(String jsonPath) {
        String path = jsonPath.startsWith(PATH_DELIMITER) ? jsonPath : PATH_DELIMITER + jsonPath;
        return path.endsWith(JSON_EXTENSION) ? path : path + JSON_EXTENSION;
    }

    private int getActiveFeatureCount() {
        return snapshot.get().getParsedFeatures().size();
    }
}
