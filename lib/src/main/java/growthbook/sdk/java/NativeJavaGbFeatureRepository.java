package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided.
 * Initialize with {@link NativeJavaGbFeatureRepository#initialize()}
 * Get the features JSON with {@link NativeJavaGbFeatureRepository#getFeaturesJson()}.
 * Get the savedGroups JSON with {@link NativeJavaGbFeatureRepository#getSavedGroupsJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 */
@Slf4j
public class NativeJavaGbFeatureRepository implements IGBFeaturesRepository {

    /**
     * Endpoint for GET request
     */
    @Getter
    private final String featuresEndpoint;

    /**
     * Endpoint for SSE request
     */
    @Getter
    private final String eventsEndpoint;

    /**
     * Strategy for building url
     */
    @Getter
    private FeatureRefreshStrategy refreshStrategy;

    /**
     * The key used to decrypt encrypted features from the API
     */
    @Nullable
    @Getter
    private final String encryptionKey;

    /**
     * Flag to know whether sse connection is allowed
     */
    private Boolean sseAllowed;

    /**
     * The standard cache TTL to use (60 seconds)
     */
    @Getter
    private final Integer swrTtlSeconds;

    /**
     * Seconds after that cache is expired
     */
    @Getter
    private Long expiresAt;

    /**
     * Flag to know whether GBFeatureRepository is initialized
     */
    private volatile Boolean initialized = false;

    /**
     * Allows you to get the saved groups JSON from the provided {@link NativeJavaGbFeatureRepository#getSavedGroupsJson()} ()}.
     * You must call {@link NativeJavaGbFeatureRepository#initialize()} before calling this method
     * or your saved groups would not have loaded.
     */
    private final AtomicReference<String> savedGroupsJson = new AtomicReference<>("{}");

    /**
     * Allows you to get the features JSON from the provided {@link NativeJavaGbFeatureRepository#getFeaturesEndpoint()}.
     * You must call {@link NativeJavaGbFeatureRepository#initialize()} before calling this method
     * or your features would not have loaded.
     */
    private final AtomicReference<String> featuresJson = new AtomicReference<>("{}");
    /**
     * Optional callbacks for getting updates when features are refreshed
     */
    private final CopyOnWriteArrayList<FeatureRefreshCallback> refreshCallbacks = new CopyOnWriteArrayList<>();
    /**
     * Lock for synchronize code and avoid race condition
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    /**
     * Create a new GBFeaturesRepository
     *
     * @param apiHost       The GrowthBook API host (default: <a href="https://cdn.growthbook.io">...</a>)
     * @param clientKey     Your client ID, e.g. sdk-abc123
     * @param encryptionKey optional key for decrypting encrypted payload
     * @param swrTtlSeconds How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     */
    @Builder
    public NativeJavaGbFeatureRepository(@Nullable String apiHost,
                                         String clientKey,
                                         @Nullable String encryptionKey,
                                         @Nullable FeatureRefreshStrategy refreshStrategy,
                                         @Nullable Integer swrTtlSeconds
    ) {
        if (clientKey == null) {
            throw new IllegalArgumentException("clientKey cannot be null");
        }
        if (apiHost == null) {
            apiHost = "https://cdn.growthbook.io";
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;
        this.featuresEndpoint = apiHost + "/api/features/" + clientKey;
        this.eventsEndpoint = apiHost + "/sub/" + clientKey;
        this.encryptionKey = encryptionKey;
        this.swrTtlSeconds = swrTtlSeconds == null ? 60 : swrTtlSeconds;
        this.refreshExpiresAt();
    }

    /**
     * Method for initialize {@link NativeJavaGbFeatureRepository}. Depends on {@link FeatureRefreshStrategy}
     * connection would be established for SSE or for just GET request
     * @throws FeatureFetchException
     */
    @Override
    public void initialize() throws FeatureFetchException {
        initialize(false);

    }

    /**
     * Get method for saved Group json
     * @return saved Group Json in format of String type
     */
    @Nullable
    public String getSavedGroupsJson() {
        return savedGroupsJson.get();
    }

    /**
     * Method for initialize {@link NativeJavaGbFeatureRepository}. Depends on {@link FeatureRefreshStrategy}
     * connection would be established for SSE or for just GET request
     * @param retryOnFailure:  Boolean argument that responsible whether SSE connection need to be reconnected
     * @throws FeatureFetchException
     */
    @Override
    public void initialize(Boolean retryOnFailure) throws FeatureFetchException {
        try {
            lock.lock();

            if (this.initialized) return;

            switch (this.refreshStrategy) {
                case STALE_WHILE_REVALIDATE:
                    fetchFeatures();
                    break;

                case SERVER_SENT_EVENTS:
                    fetchFeatures();
                    initializeSSE(retryOnFailure);
                    break;
            }

            this.initialized = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Method for getting Feature from API call once if it STALE_WHILE_REVALIDATE or be updated if SERVER_SENT_EVENTS strategy
     * @return Feature Json in format of String
     */
    @Override
    public String getFeaturesJson() {
        try {
            lock.lock();

            switch (this.refreshStrategy) {
                case STALE_WHILE_REVALIDATE:
                    if (isCacheExpired()) {
                        this.initializeSSE(true);
                        this.refreshExpiresAt();
                    }
                    return this.featuresJson.get();

                case SERVER_SENT_EVENTS:
                    return this.featuresJson.get();
            }

            return this.featuresJson.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Subscribe to feature refresh events
     * This callback is called when the features are successfully refreshed or there is an error when refreshing.
     * This is called even if the features have not changed.
     *
     * @param callback This callback will be called when features are refreshed
     */
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        this.refreshCallbacks.add(callback);
    }

    /**
     * Clears the feature refresh callbacks
     */
    @Override
    public void clearCallbacks() {
        this.refreshCallbacks.clear();
    }

    private void refreshExpiresAt() {
        this.expiresAt = Instant.now().getEpochSecond() + this.swrTtlSeconds;
    }

    private void fetchFeatures() throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(this.featuresEndpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(Constants.GET_METHOD);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String lines;
                while ((lines = reader.readLine()) != null) {
                    responseBuilder.append(lines);
                }
                reader.close();
                String responseBody = responseBuilder.toString();
                String sseSupportHeader = connection.getHeaderField(Constants.X_SSE_SUPPORT);
                if (sseSupportHeader == null) {
                    throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN);
                }
                this.sseAllowed = Constants.ENABLED.equals(sseSupportHeader);
                this.onSuccess(responseBody);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    private void onSuccess(String response) throws FeatureFetchException {
        if (response == null) {
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR
            );
        }
        onResponseJson(response);
    }

    private void onResponseJson(String responseJsonString) throws FeatureFetchException {
        try {
            lock.lock();
            if (responseJsonString == null || responseJsonString.trim().isEmpty()) {
                return;
            }

            try {
                JsonObject jsonObject = GrowthBookJsonUtils.getInstance()
                        .gson.fromJson(responseJsonString, JsonObject.class);

                if (jsonObject == null) {
                    log.error("JSON response is null or invalid");
                    throw new FeatureFetchException(
                            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                            "JSON response is null or invalid"
                    );
                }

                String refreshedFeatures;
                String refreshedSavedGroups = "";

                if (this.encryptionKey != null) {
                    JsonElement encryptedFeaturesJsonElement = jsonObject.get(Constants.ENCRYPTED_FEATURES_KEY);
                    JsonElement encryptedSavedGroupsJsonElement = jsonObject.get(Constants.ENCRYPTED_SAVED_GROUPS_KEY);
                    if (encryptedFeaturesJsonElement == null) {
                        log.error("encryptionKey provided but endpoint not encrypted");

                        throw new FeatureFetchException(
                                FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                                "encryptionKey provided but endpoint not encrypted"
                        );
                    }
                    if (encryptedSavedGroupsJsonElement != null) {
                        String encryptedSavedGroupsJson = encryptedSavedGroupsJsonElement.getAsString();
                        refreshedSavedGroups = DecryptionUtils.decrypt(encryptedSavedGroupsJson, this.encryptionKey).trim();
                    }

                    String encryptedFeaturesJson = encryptedFeaturesJsonElement.getAsString();
                    refreshedFeatures = DecryptionUtils.decrypt(encryptedFeaturesJson, this.encryptionKey).trim();
                } else {
                    JsonElement featuresJsonElement = jsonObject.get(Constants.FEATURE_KEY);
                    JsonElement savedGroupJsonElement = jsonObject.get(Constants.SAVED_GROUP_KEY);

                    if (featuresJsonElement == null) {
                        log.error("No features found");
                        throw new FeatureFetchException(
                                FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                                "No features found"
                        );
                    }

                    refreshedFeatures = featuresJsonElement.toString().trim();
                    if (savedGroupJsonElement != null) {
                        refreshedSavedGroups = savedGroupJsonElement.toString().trim();
                    }
                }

                this.featuresJson.set(refreshedFeatures);
                this.savedGroupsJson.set(refreshedSavedGroups);

                this.onRefreshSuccess(this.featuresJson.get());
            } catch (DecryptionUtils.DecryptionException e) {
                log.error("DecryptionException exception occur, when try to parse: {}. {}",
                        responseJsonString, e.getMessage(), e);

                throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                        e.getMessage()
                );
            }
        }finally {
            lock.unlock();
        }
    }

    private void onRefreshSuccess(String featuresJson) {
        this.refreshCallbacks.forEach(featureRefreshCallback -> featureRefreshCallback.onRefresh(featuresJson));
    }

    private Boolean isCacheExpired() {
        long now = Instant.now().getEpochSecond();
        return now >= this.expiresAt;
    }


    private void initializeSSE(Boolean retryOnFailure) {
        if (!this.sseAllowed) {
            log.info("\nFalling back to stale-while-revalidate refresh strategy. 'X-Sse-Support: enabled' not present on resource returned at {}", this.featuresEndpoint);
            this.refreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        }

        Runnable sseTask = new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = null;
                HttpURLConnection connection = null;
                try {
                    connection = establishSseConnection(eventsEndpoint);

                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    StringBuilder dataBuffer = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(Constants.DATA_SSE_KEY)) {
                            dataBuffer.append(line.substring(Constants.QUANTITY_TO_CUT_SSE).trim()).append("\n");
                        } else if (line.isEmpty()) {
                            String data = dataBuffer.toString();
                            if (!data.isEmpty()) {
                                onResponseJson(data);
                            }
                            dataBuffer.setLength(0);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed into SSE connection. Try to reconnect {}", e.getMessage(), e);
                    if (retryOnFailure) {
                        run();
                    }
                } finally {
                    try {
                        if (reader != null) reader.close();
                        if (connection != null) connection.disconnect();
                    } catch (IOException e) {
                        log.error("BufferedReader unsuccessfully closed: {}", e.getMessage(), e);
                    }
                }
            }
        };
        new Thread(sseTask).start();
    }


    private HttpURLConnection establishSseConnection(String sseEndPoint) throws FeatureFetchException {
        HttpURLConnection connection;
        URL url;
        try {
            url = new URL(sseEndPoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(Constants.GET_METHOD);
            connection.setRequestProperty(Constants.ACCEPT, Constants.SSE_HEADER);
            connection.setDoInput(true);
            connection.connect();

        } catch (IOException e) {
            log.error("Exception occur while establishing SSE connection: {}", e.getMessage(), e);
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.SSE_CONNECTION_ERROR,
                    e.getMessage());
        }
        return connection;
    }
}
