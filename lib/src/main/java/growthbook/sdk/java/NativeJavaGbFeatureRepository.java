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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String ENABLED = "enabled";
    private static final int QUANTITY_TO_CUT_SSE = 5;
    private static final String FILE_NAME_FOR_CACHE = "FEATURE_CACHE.json";
    public static final String FILE_PATH_FOR_CACHE = "src/main/resources";
    public static final String EMPTY_JSON_OBJECT_STRING = "{}";

    /**
     * Endpoint for GET request
     */
    @Getter
    private final String featuresEndpoint;

    /**
     * Endpoint for POST request
     */
    @Getter
    private final String remoteEvalEndPoint;

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
    private final AtomicBoolean sseAllowed = new AtomicBoolean(false);

    /**
     * The standard cache TTL to use (60 seconds)
     */
    @Getter
    private final AtomicInteger swrTtlSeconds;

    /**
     * Seconds after that cache is expired
     */
    @Getter
    private final AtomicLong expiresAt = new AtomicLong(0);

    /**
     * Flag to know whether GBFeatureRepository is initialized
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Allows you to get the saved groups JSON from the provided {@link NativeJavaGbFeatureRepository#getSavedGroupsJson()} ()}.
     * You must call {@link NativeJavaGbFeatureRepository#initialize()} before calling this method
     * or your saved groups would not have loaded.
     */
    private final AtomicReference<String> savedGroupsJson = new AtomicReference<>(EMPTY_JSON_OBJECT_STRING);

    /**
     * Allows you to get the features JSON from the provided {@link NativeJavaGbFeatureRepository#getFeaturesEndpoint()}.
     * You must call {@link NativeJavaGbFeatureRepository#initialize()} before calling this method
     * or your features would not have loaded.
     */
    private final AtomicReference<String> featuresJson = new AtomicReference<>(EMPTY_JSON_OBJECT_STRING);
    /**
     * Optional callbacks for getting updates when features are refreshed
     */
    private final CopyOnWriteArrayList<FeatureRefreshCallback> refreshCallbacks = new CopyOnWriteArrayList<>();
    /**
     * Lock for synchronize code and avoid race condition
     */
    private final ReentrantLock lock = new ReentrantLock(true);
    /**
     * CachingManger allows to cache features data to file
     */
    private AtomicReference<CachingManager> cachingManager;
    /**
     * Flag that enable CachingManager
     */
    private final AtomicBoolean isCacheDisabled;

    /**
     * Request body that be sent with POST request for remote eval feature
     */
    @Nullable
    @Getter
    private final RequestBodyForRemoteEval requestBodyForRemoteEval;

    /**
     * Create a new GBFeaturesRepository
     *
     * @param apiHost       The GrowthBook API host (default: <a href="https://cdn.growthbook.io">...</a>)
     * @param clientKey     Your client ID, e.g. sdk-abc123
     * @param encryptionKey optional key for decrypting encrypted payload
     * @param swrTtlSeconds How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     * @param requestBodyForRemoteEval       Payload that would be sent with POST request when repository configure with Remote evalStrategy {@link FeatureRefreshStrategy#REMOTE_EVAL_STRATEGY}
     */
    @Builder
    public NativeJavaGbFeatureRepository(@Nullable String apiHost,
                                         String clientKey,
                                         @Nullable String encryptionKey,
                                         @Nullable FeatureRefreshStrategy refreshStrategy,
                                         @Nullable Integer swrTtlSeconds,
                                         @Nullable Boolean isCacheDisabled,
                                         @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval
    ) {
        this.isCacheDisabled = new AtomicBoolean(Boolean.TRUE.equals(isCacheDisabled));
        if (clientKey == null) {
            throw new IllegalArgumentException("clientKey cannot be null");
        }
        if (apiHost == null) {
            apiHost = "https://cdn.growthbook.io";
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;
        this.featuresEndpoint = apiHost + "/api/features/" + clientKey;
        this.eventsEndpoint = apiHost + "/sub/" + clientKey;
        this.remoteEvalEndPoint = apiHost + "/api/eval/" + clientKey;
        this.requestBodyForRemoteEval = requestBodyForRemoteEval;

        this.encryptionKey = encryptionKey;
        this.swrTtlSeconds = swrTtlSeconds == null ? new AtomicInteger(60) : new AtomicInteger(swrTtlSeconds);
        this.refreshExpiresAt();
            if (!this.isCacheDisabled.get()) {
                this.cachingManager = new AtomicReference<>(new CachingManager(FILE_PATH_FOR_CACHE));
            }

    }

    /**
     * Method for initialize {@link NativeJavaGbFeatureRepository}. Depends on {@link FeatureRefreshStrategy}
     * connection would be established for SSE or for just GET request
     *
     * @throws FeatureFetchException while initialize function
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
     * @throws FeatureFetchException during fetchFeatures function
     */
    @Override
    public void initialize(Boolean retryOnFailure) throws FeatureFetchException {
        try {
            lock.lock();

            if (this.initialized.get()) return;

            switch (this.refreshStrategy) {
                case STALE_WHILE_REVALIDATE:
                    fetchFeatures();
                    break;

                case SERVER_SENT_EVENTS:
                    fetchFeatures();
                    initializeSSE(retryOnFailure);
                    break;

                case REMOTE_EVAL_STRATEGY:
                    fetchForRemoteEval(this.requestBodyForRemoteEval);
                    break;
            }

            this.initialized.set(true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Method for getting Feature from API call once if it STALE_WHILE_REVALIDATE or be updated if SERVER_SENT_EVENTS strategy
     *
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

                case REMOTE_EVAL_STRATEGY:
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
        this.expiresAt.set(
                Instant.now().getEpochSecond() + this.swrTtlSeconds.get()
        );
    }

    void fetchFeatures() throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(this.featuresEndpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HttpMethods.GET.getMethod());

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String lines;
                while ((lines = reader.readLine()) != null) {
                    responseBuilder.append(lines);
                }
                reader.close();
                String responseBody = responseBuilder.toString();
                String sseSupportHeader = connection.getHeaderField(HttpHeaders.X_SSE_SUPPORT.getHeader());
                if (sseSupportHeader == null) {
                    throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN);
                }
                this.sseAllowed.set(ENABLED.equals(sseSupportHeader));
                this.onSuccess(responseBody, false);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            if (!isCacheDisabled.get()) {
                String cachedData = getCachedFeatures();
                onResponseJson(cachedData, true);
            } else {
                this.onRefreshFailed(e);

                throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                        e.getMessage());
            }

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


    private void onSuccess(String response, boolean isFromCache) throws FeatureFetchException {
        String responseJsonString;
        if (response != null) {
            responseJsonString = response;
        }else {
            log.error("FeatureFetchException: FeatureFetchErrorCode.NO_RESPONSE_ERROR");
            log.info("Fetching data from cache...");
            responseJsonString = getCachedFeatures();
            isFromCache = true;
        }
        onResponseJson(responseJsonString, isFromCache);
    }

    private void onResponseJson(String responseJsonString, boolean isFromCache) throws FeatureFetchException {
        try {
            lock.lock();
            if (responseJsonString == null || responseJsonString.trim().isEmpty()) {
                return;
            }

            if (!isFromCache && !isCacheDisabled.get()) {
                cachingManager.get().saveContent(FILE_NAME_FOR_CACHE, responseJsonString);
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
                    JsonElement encryptedFeaturesJsonElement = jsonObject.get(FeatureResponseKey.ENCRYPTED_FEATURES_KEY.getKey());
                    JsonElement encryptedSavedGroupsJsonElement = jsonObject.get(FeatureResponseKey.ENCRYPTED_SAVED_GROUPS_KEY.getKey());
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
                    JsonElement featuresJsonElement = jsonObject.get(FeatureResponseKey.FEATURE_KEY.getKey());
                    JsonElement savedGroupJsonElement = jsonObject.get(FeatureResponseKey.SAVED_GROUP_KEY.getKey());

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
        } finally {
            lock.unlock();
        }
    }

    void onRefreshSuccess(String featuresJson) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            callback.onRefresh(featuresJson);
        }
    }

    void onRefreshFailed(Throwable throwable) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            callback.onError(throwable);
        }
    }

    private Boolean isCacheExpired() {
        long now = Instant.now().getEpochSecond();
        return now >= this.expiresAt.get();
    }


    private void initializeSSE(Boolean retryOnFailure) {
        if (!this.sseAllowed.get()) {
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
                        if (line.startsWith(SseKey.DATA.getKey())) {
                            dataBuffer.append(line.substring(QUANTITY_TO_CUT_SSE).trim()).append("\n");
                        } else if (line.isEmpty()) {
                            String data = dataBuffer.toString();
                            if (!data.isEmpty()) {
                                onResponseJson(data, false);
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

    private void fetchForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        HttpURLConnection urlConnection = null;
        try {
            String body = GrowthBookJsonUtils.getInstance().gson.toJson(requestBodyForRemoteEval);

            URL url = new URL(this.remoteEvalEndPoint);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept", "application/json");

            try (OutputStream os = urlConnection.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuilder builder = new StringBuilder();

                while ((inputLine = bufferedReader.readLine()) != null) {
                    builder.append(inputLine);
                }
                bufferedReader.close();
                String jsonResponse = builder.toString();
                onSuccess(jsonResponse, false);
            } else {
                onRefreshFailed(new Throwable(
                        "Response is not success. Response code: " + urlConnection.getResponseCode() + ". Message: " + urlConnection.getResponseMessage()
                ));
            }
        } catch (IOException e) {
            onRefreshFailed(e);
            log.error("Exception occur with message: {}", e.getMessage(), e);
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }


    private HttpURLConnection establishSseConnection(String sseEndPoint) throws FeatureFetchException {
        HttpURLConnection connection;
        URL url;
        try {
            url = new URL(sseEndPoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HttpMethods.GET.getMethod());
            connection.setRequestProperty(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.SSE_HEADER.getHeader());
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
    private String getCachedFeatures() throws FeatureFetchException {
        String cachedData = cachingManager.get().loadCache(FILE_NAME_FOR_CACHE);
        if (cachedData == null) {
            log.error("FeatureFetchException: No Features from Cache");
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR);
        }
        return cachedData;
    }
}
