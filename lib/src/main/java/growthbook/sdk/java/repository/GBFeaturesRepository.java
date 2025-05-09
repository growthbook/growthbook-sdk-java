package growthbook.sdk.java.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
import growthbook.sdk.java.util.DecryptionUtils;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.model.FeatureResponseKey;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.model.GBContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided.
 * Initialize with {@link GBFeaturesRepository#initialize()}
 * Get the features JSON with {@link GBFeaturesRepository#getFeaturesJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 */
@Slf4j
public class GBFeaturesRepository implements IGBFeaturesRepository {
    private static final String ENABLED = "enabled";
    private static final String FILE_NAME = "FEATURE_CACHE.json";
    public static final String FILE_PATH_FOR_CACHE = "src/main/resources";
    public static final String EMPTY_JSON_OBJECT_STRING = "{}";

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
     * @deprecated Use decryptionKey instead.
     */
    @Nullable
    @Deprecated
    private final String encryptionKey;

    /**
     * The key used to decrypt encrypted features from the API
     */
    @Nullable
    @Getter
    private final String decryptionKey;

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
     * Http request client for send GET request
     */
    private final OkHttpClient okHttpClient;

    /**
     * Http request client for establish SSE connection
     */
    @Nullable
    private OkHttpClient sseHttpClient;

    /**
     * Optional callbacks for getting updates when features are refreshed
     */
    private final ArrayList<FeatureRefreshCallback> refreshCallbacks = new ArrayList<>();

    /**
     * Flag to know whether GBFeatureRepository is initialized
     */
    @Getter
    private Boolean initialized = false;

    /**
     * Flag to know whether sse connection is allowed
     */
    private Boolean sseAllowed = false;
    @Nullable
    private Request sseRequest = null;
    @Nullable
    private EventSource sseEventSource = null;

    /**
     * Allows you to get the saved groups JSON from the provided {@link GBFeaturesRepository#getFeaturesEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your saved groups would not have loaded.
     */
    @Getter
    @Nullable
    private String savedGroupsJson = EMPTY_JSON_OBJECT_STRING;

    /**
     * Allows you to get the features JSON from the provided {@link GBFeaturesRepository#getFeaturesEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your features would not have loaded.
     */
    private String featuresJson = EMPTY_JSON_OBJECT_STRING;

    /**
     * Keys are unique identifiers for the features and the values are Feature objects.
     * Feature definitions - To be pulled from API / Cache
     */
    //@Getter
    private Map<String, Feature<?>> parsedFeatures = new HashMap<>();

    @Getter
    private JsonObject parsedSavedGroups = new JsonObject();

    public void setCacheManager(GbCacheManager cacheManager) {
        if (!isCacheDisabled) {
            this.cacheManager = cacheManager;
        }
    }

    /**
     * CachingManger allows to cache features data to file
     */
    @Getter
    private GbCacheManager cacheManager;

    /**
     * Flag that enable CachingManager
     */
    private final boolean isCacheDisabled;

    /**
     * Request body for that be sent with POST request for remote eval feature
     */
    @Nullable
    @Getter
    private final RequestBodyForRemoteEval requestBodyForRemoteEval;
    /**
     * Endpoint for POST request
     */
    @Getter
    private final String remoteEvalEndPoint;


    /**
     * Create a new GBFeaturesRepository
     *
     * @param apiHost       The GrowthBook API host (default: <a href="https://cdn.growthbook.io">...</a>)
     * @param clientKey     Your client ID, e.g. sdk-abc123
     * @param encryptionKey optional key for decrypting encrypted payload
     * @param swrTtlSeconds How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     */
    //@Builder
    @Deprecated
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Deprecated @Nullable String encryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds
    ) {
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, null, null, null, null);
    }

    /**
     * New constructor that support payload for remote eval
     *
     * @param apiHost                  The GrowthBook API host (default: <a href="https://cdn.growthbook.io">...</a>)
     * @param clientKey                Your client ID, e.g. sdk-abc123
     * @param encryptionKey            optional key for decrypting encrypted payload
     * @param refreshStrategy          Strategy for building url
     * @param swrTtlSeconds            How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     * @param requestBodyForRemoteEval Payload that would be sent with POST request when repository configure with Remote evalStrategy  {@link FeatureRefreshStrategy#REMOTE_EVAL_STRATEGY} (default: 60)
     */
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Deprecated @Nullable String encryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval
    ) {
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, null, null, requestBodyForRemoteEval, null);
    }
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Deprecated @Nullable String encryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable Boolean isCacheDisabled
    ) {
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, null, null, isCacheDisabled, null,null);
    }

    /**
     * New constructor that explicitly supports decryptionKey.
     */
    @Builder
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Deprecated @Nullable String encryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable OkHttpClient okHttpClient,
            @Nullable String decryptionKey,
            @Nullable Boolean isCacheDisabled,
            @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval,
            @Nullable GbCacheManager cacheManager
    ) {
        this(apiHost, clientKey, (decryptionKey != null) ? decryptionKey : encryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                isCacheDisabled,
                (requestBodyForRemoteEval != null) ? requestBodyForRemoteEval : new RequestBodyForRemoteEval(),
                cacheManager
        );
    }

    /**
     * Create a new GBFeaturesRepository
     *
     * @param apiHost                  The GrowthBook API host (default: <a href="https://cdn.growthbook.io">...</a>)
     * @param clientKey                Your client ID, e.g. sdk-abc123
     * @param decryptionKey            optional key for decrypting encrypted payload
     * @param swrTtlSeconds            How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     * @param okHttpClient             HTTP client (optional)
     * @param isCacheDisabled          Parameter to disable or enable caching in project
     * @param requestBodyForRemoteEval Payload that would be sent with POST request when repository configure with Remote evalStrategy {@link FeatureRefreshStrategy#REMOTE_EVAL_STRATEGY}
     */
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Nullable String decryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable OkHttpClient okHttpClient,
            @Nullable Boolean isCacheDisabled,
            @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval,
            @Nullable GbCacheManager cacheManager
    ) {
        this.isCacheDisabled = isCacheDisabled != null && isCacheDisabled; // cache enable by default
        if (clientKey == null) throw new IllegalArgumentException("clientKey cannot be null");

        // Set the defaults when the user does not provide them
        if (apiHost == null) {
            apiHost = "https://cdn.growthbook.io";
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;

        // Build the endpoints from the apiHost and clientKey
        this.featuresEndpoint = apiHost + "/api/features/" + clientKey;
        this.eventsEndpoint = apiHost + "/sub/" + clientKey;
        this.remoteEvalEndPoint = apiHost + "/api/eval/" + clientKey;

        this.encryptionKey = decryptionKey;
        this.decryptionKey = decryptionKey;

        this.swrTtlSeconds = swrTtlSeconds == null ? 60 : swrTtlSeconds;
        this.requestBodyForRemoteEval = requestBodyForRemoteEval != null ? requestBodyForRemoteEval : new RequestBodyForRemoteEval();
        this.refreshExpiresAt();

        // Use provided OkHttpClient or create a new one
        if (okHttpClient == null) {
            this.okHttpClient = this.initializeHttpClient();
        } else {
            // TODO: Check for valid interceptor
            this.okHttpClient = okHttpClient;
        }
        if (Boolean.FALSE.equals(this.isCacheDisabled)) {
            this.cacheManager = cacheManager != null ? cacheManager : new FileCachingManagerImpl(FILE_PATH_FOR_CACHE);
        }
    }

    // Getter for deprecated encryptionKey
    @Deprecated
    @Nullable
    public String getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * @return feature data JSON in a type of String. Handle refresh strategy
     */
    public String getFeaturesJson() {
        if (this.refreshStrategy == FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
                && !isCacheDisabled
                && isCacheExpired()
        ) {
            this.enqueueFeatureRefreshRequest();
            this.refreshExpiresAt();
        }
        return this.featuresJson;
    }

    public Map<String, Feature<?>> getParsedFeatures() {
        //TBD: This auto-refresh implementation must be corrected.
        if (this.refreshStrategy == FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
                && !isCacheDisabled
                && isCacheExpired()
        ) {
            this.enqueueFeatureRefreshRequest();
            this.refreshExpiresAt();
        }
        return this.parsedFeatures;
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

    @Override
    public void clearCallbacks() {
        this.refreshCallbacks.clear();
    }

    private void enqueueFeatureRefreshRequest() {
        GBFeaturesRepository self = this;

        Request request = new Request.Builder()
                .url(this.featuresEndpoint)
                .build();

        this.okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (!isCacheDisabled) {
                    try {
                        String cachedData = getCachedFeatures();
                        onResponseJson(cachedData, true);
                    } catch (FeatureFetchException ex) {
                        log.error(e.getMessage(), e);

                    }
                }
                    // OkHttp will auto-retry on failure
                    self.onRefreshFailed(e);

            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try {
                    self.onSuccess(response);
                } catch (FeatureFetchException e) {
                    log.error(e.getMessage(), e);
                    if (!isCacheDisabled) {
                        try {
                            String cachedData = getCachedFeatures();
                            onResponseJson(cachedData, true);
                        } catch (FeatureFetchException ex) {
                            log.error(e.getMessage(), e);

                        }
                    }
                }
            }
        });
    }

    @Override
    public void initialize() throws FeatureFetchException {
        initialize(false);
    }

    @Override
    public void initialize(Boolean retryOnFailure) throws FeatureFetchException {
        if (this.initialized) return;

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

        this.initialized = true;
    }

    private void initializeSSE(Boolean retryOnFailure) {
        if (!this.sseAllowed) {
            log.info("\nFalling back to stale-while-revalidate refresh strategy. 'X-Sse-Support: enabled' not present on resource returned at {}", this.featuresEndpoint);
            this.refreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        }

        createEventSourceListenerAndStartListening(retryOnFailure);
    }

    /**
     * Creates an SSE HTTP client if null.
     * Creates and enqueues a new asynchronous request to the events' endpoint.
     * Assigns a close listener to recreate the connection.
     */
    private void createEventSourceListenerAndStartListening(Boolean retryOnFailure) {
        this.sseEventSource = null;
        this.sseRequest = null;

        if (this.sseHttpClient == null) {
            this.sseHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
                    .retryOnConnectionFailure(true)
                    .connectTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .writeTimeout(0, TimeUnit.SECONDS)
                    .build();
        }

        this.sseRequest = new Request.Builder()
                .url(this.eventsEndpoint)
                .header(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.APPLICATION_JSON.getHeader())
                .addHeader(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.SSE_HEADER.getHeader())
                .build();

        GBEventSourceListener gbEventSourceListener =
                new GBEventSourceListener(
                        new GBEventSourceHandler() {
                            @Override
                            public void onClose(EventSource eventSource) {
                                eventSource.cancel();
                                createEventSourceListenerAndStartListening(retryOnFailure);
                            }

                            @Override
                            public void onFeaturesResponse(String featuresJsonResponse) throws FeatureFetchException {
                                onResponseJson(featuresJsonResponse, false);
                            }
                        }
                ) {
                    @Override
                    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                        super.onFailure(eventSource, t, response);
                        if (retryOnFailure) {
                            createEventSourceListenerAndStartListening(true);

                            try {
                                fetchFeatures();
                            } catch (FeatureFetchException featureFetchException) {
                                Logger.getAnonymousLogger()
                                        .throwing(
                                                "GBFeaturesRepository",
                                                "createEventSourceListenerAndStartListening()",
                                                featureFetchException
                                        );
                            }
                        }
                    }
                };

        this.sseEventSource = EventSources
                .createFactory(this.sseHttpClient)
                .newEventSource(sseRequest, gbEventSourceListener);

        this.sseHttpClient.newCall(sseRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                log.error("SSE connection failed: {}", e.getMessage(), e);
                call.cancel();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                response.close();
            }
        });
    }

    /**
     * @return A new {@link OkHttpClient} with an interceptor {@link GBFeaturesRepositoryRequestInterceptor}
     */
    private OkHttpClient initializeHttpClient() {

        return new OkHttpClient.Builder()
                .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
                .retryOnConnectionFailure(true)
                .build();
    }

    private void refreshExpiresAt() {
        this.expiresAt = Instant.now().getEpochSecond() + this.swrTtlSeconds;
    }

    private Boolean isCacheExpired() {
        long now = Instant.now().getEpochSecond();
        return now >= this.expiresAt;
    }

    /**
     * Performs a network request to fetch the features from the GrowthBook API
     * with the provided endpoint.
     * If an encryptionKey is provided, it is assumed the features endpoint is using encrypted features.
     * This method will attempt to decrypt the encrypted features with the provided encryptionKey.
     */
    public void fetchFeatures() throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        Request request = new Request.Builder()
                .url(this.featuresEndpoint)
                .build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            String sseSupportHeader = response.header(HttpHeaders.X_SSE_SUPPORT.getHeader());
            this.sseAllowed = Objects.equals(sseSupportHeader, ENABLED);

            this.onSuccess(response);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            if (!isCacheDisabled) {
                String cachedData = getCachedFeatures();
                onResponseJson(cachedData, true);
            }
        }
    }

    /**
     * Reads the response JSON properties `features` or `encryptedFeatures`, and decrypts if necessary
     *
     * @param responseJsonString JSON response object
     */
    private void onResponseJson(String responseJsonString, boolean isFromCache) throws FeatureFetchException {
        try {
            if (!isFromCache && !isCacheDisabled) {
                cacheManager.saveContent(FILE_NAME, responseJsonString);
            }

            JsonObject jsonObject = GrowthBookJsonUtils.getInstance()
                    .gson.fromJson(responseJsonString, JsonObject.class);

            // Features will be refreshed as either an encrypted or un-encrypted JSON string
            String refreshedFeatures;
            String refreshedSavedGroups = "";

            if (this.decryptionKey != null) {
                // Use encrypted features at responseBody.encryptedFeatures
                JsonElement encryptedFeaturesJsonElement = jsonObject.get(FeatureResponseKey.ENCRYPTED_FEATURES_KEY.getKey());
                JsonElement encryptedSavedGroupsJsonElement = jsonObject.get(FeatureResponseKey.ENCRYPTED_SAVED_GROUPS_KEY.getKey());
                if (encryptedFeaturesJsonElement == null) {
                    log.error(
                            "FeatureFetchException: CONFIGURATION_ERROR feature fetch error code: "
                                    + "encryptionKey provided but endpoint not encrypted");
                    throw new FeatureFetchException(
                            FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                            "encryptionKey provided but endpoint not encrypted"
                    );
                }

                String encryptedFeaturesJson = encryptedFeaturesJsonElement.getAsString();
                String encryptedSavedGroupsJson;
                if (encryptedSavedGroupsJsonElement != null) {
                    encryptedSavedGroupsJson = encryptedSavedGroupsJsonElement.getAsString();
                    refreshedSavedGroups = DecryptionUtils.decrypt(encryptedSavedGroupsJson, this.decryptionKey).trim();
                }

                refreshedFeatures = DecryptionUtils.decrypt(encryptedFeaturesJson, this.decryptionKey).trim();
            } else {
                // Use unencrypted features at responseBody.features
                JsonElement featuresJsonElement = jsonObject.get(FeatureResponseKey.FEATURE_KEY.getKey());
                JsonElement savedGroupsJsonElement = jsonObject.get(FeatureResponseKey.SAVED_GROUP_KEY.getKey());

                if (featuresJsonElement == null) {
                    log.error(
                            "FeatureFetchException: CONFIGURATION_ERROR feature fetch error code: "
                                    + "No features found");

                    throw new FeatureFetchException(
                            FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                            "No features found"
                    );
                }

                if (savedGroupsJsonElement != null) {
                    refreshedSavedGroups = savedGroupsJsonElement.toString().trim();
                }

                refreshedFeatures = featuresJsonElement.toString().trim();
            }

            this.featuresJson = refreshedFeatures;
            this.savedGroupsJson = refreshedSavedGroups;
            this.parsedFeatures = TransformationUtil.transformFeatures(this.featuresJson);
            this.parsedSavedGroups = TransformationUtil.transformSavedGroups(this.savedGroupsJson);

            if (!isFromCache) {
                this.onRefreshSuccess(this.featuresJson);
            }
        } catch (DecryptionUtils.DecryptionException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}",
                    e.getMessage(), e);

            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    e.getMessage()
            );
        }
    }

    private void onRefreshSuccess(String featuresJson) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            if (callback != null) {
                callback.onRefresh(featuresJson);
            }
        }
    }

    private void onRefreshFailed(Throwable throwable) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            if (callback != null) {
                callback.onError(throwable);
            }
        }
    }

    /**
     * Handles the successful features fetching response
     *
     * @param response Successful response
     */
    private void onSuccess(Response response) throws FeatureFetchException {
        try {
            ResponseBody responseBody = response.body();

            // if response code is not 200 or response body is null - try cache
            if (response.code() != 200 || responseBody == null) {
                log.error("FeatureFetchException: {} with status {}, response: {}",
                        responseBody == null ? FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR : FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                        response.code(),
                        responseBody != null ? responseBody.string() : "null");

                if (isCacheDisabled) {
                    throw new FeatureFetchException(
                            FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                            "Failed to fetch data from server and cache is disabled"
                    );
                }
                log.info("Fetching data from cache...");

                String featuresFromCache = getCachedFeatures();
                if (featuresFromCache.isEmpty()) {
                    throw new FeatureFetchException(
                            FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                            "Failed to fetch data from cache"
                    );
                }
                onResponseJson(featuresFromCache, true);
                return;
            }
            onResponseJson(responseBody.string(), false);

        } catch (IOException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}", e.getMessage(), e);
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    e.getMessage()
            );
        }
    }

    private interface GBEventSourceHandler {
        void onClose(EventSource eventSource);

        void onFeaturesResponse(String featuresJsonResponse) throws FeatureFetchException;
    }

    private static class GBEventSourceListener extends EventSourceListener {
        private final GBEventSourceHandler handler;

        public GBEventSourceListener(GBEventSourceHandler handler) {
            this.handler = handler;
        }

        @Override
        public void onClosed(@NotNull EventSource eventSource) {
            super.onClosed(eventSource);
            handler.onClose(eventSource);
        }

        @Override
        public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
            super.onEvent(eventSource, id, type, data);

            if (data.trim().isEmpty()) return;

            try {
                handler.onFeaturesResponse(data);
            } catch (FeatureFetchException e) {
                log.error(e.getMessage(), e);
            }
        }

        @Override
        public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
            super.onFailure(eventSource, t, response);
        }

        @Override
        public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            super.onOpen(eventSource, response);
        }
    }

    public void shutdown() {
        if (this.sseEventSource != null) {
            this.sseEventSource.cancel();
            this.sseEventSource = null;
            log.info("SseEventSource cancel");
        }
        if (this.sseHttpClient != null) {
            this.sseHttpClient.dispatcher().cancelAll();
            this.sseHttpClient.connectionPool().evictAll();
            if (this.sseHttpClient.cache() != null) {
                try {
                    this.sseHttpClient.cache().close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
            this.sseHttpClient = null;
            log.info("SseHttpClient shutdown");
            this.cacheManager.clearCache();
            this.cacheManager = null;
            log.info("CacheManager shutdown");

        }
    }

    public void fetchForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        if (this.remoteEvalEndPoint == null) {
            throw new IllegalArgumentException("remote eval features endpoint cannot be null");
        }
        String jsonBody = GrowthBookJsonUtils.getInstance().gson.toJson(requestBodyForRemoteEval);
        RequestBody requestBody = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(this.remoteEvalEndPoint)
                .post(requestBody)
                .build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.code() == 200) {
                onSuccess(response);
            } else {
                onRefreshFailed(new Throwable("Response is not success, response code is:" + response.code() + ". And message is: " + response.message()));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, e.getMessage());
        }
    }


    private String getCachedFeatures() throws FeatureFetchException {
        String cachedData = cacheManager.loadCache(FILE_NAME);
        if (cachedData == null) {
            log.error("FeatureFetchException: No Features from Cache");
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR);
        }
        return cachedData;
    }
}
