package growthbook.sdk.java.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.RetryableFeatureFetchException;
import growthbook.sdk.java.featurefetch.FeatureFetchFailureHandler;
import growthbook.sdk.java.featurefetch.FeatureFetchHttpStatus;
import growthbook.sdk.java.featurefetch.FeatureRefreshCacheFreshness;
import growthbook.sdk.java.featurefetch.FeatureRefreshScheduler;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.retry.FeatureFetchRetryExecutor;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.util.DecryptionUtils;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.model.FeatureResponseKey;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.HttpMethods;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.model.SseKey;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.sse.SseEventPayloadValidator;
import growthbook.sdk.java.remoteeval.RemoteEvalEndpoints;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static final String FEATURES_PATH_PATTERN = ".*/api/features/[^/]+";

    /**
     * Thread-safe LRU cache with max 100 entries to prevent unbounded growth
     */
    private final LruETagCache eTagCache = new LruETagCache(100);

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
     * Optional minimum interval between non-forced feature refreshes.
     */
    @Nullable
    @Getter
    private final Duration backgroundFetchInterval;

    /**
     * Bounded retry policy for feature fetch requests.
     */
    @Getter
    private final FeatureFetchRetryPolicy retryPolicy;

    private final FeatureFetchRetryExecutor featureFetchRetryExecutor;
    private final FeatureRefreshScheduler featureRefreshScheduler;

    private final AtomicLong lastSuccessfulFetchAtMillis = new AtomicLong(0);
    private final AtomicBoolean hasFeatureData = new AtomicBoolean(false);

    /**
     * Seconds after that cache is expired
     */
    @Getter
    private final AtomicLong expiresAt = new AtomicLong(0);

    /**
     * Flag to know whether GBFeatureRepository is initialized
     */
    @Getter
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
    private final AtomicReference<GbCacheManager> cacheManager;
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
    public NativeJavaGbFeatureRepository(@Nullable String apiHost,
                                         String clientKey,
                                         @Nullable String encryptionKey,
                                         @Nullable FeatureRefreshStrategy refreshStrategy,
                                         @Nullable Integer swrTtlSeconds,
                                         @Nullable Boolean isCacheDisabled,
                                         @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval,
                                         @Nullable GbCacheManager cacheManager
    ) {
        this(
                apiHost,
                clientKey,
                encryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                isCacheDisabled,
                requestBodyForRemoteEval,
                cacheManager,
                null,
                null
        );
    }

    @Builder
    public NativeJavaGbFeatureRepository(@Nullable String apiHost,
                                         String clientKey,
                                         @Nullable String encryptionKey,
                                         @Nullable FeatureRefreshStrategy refreshStrategy,
                                         @Nullable Integer swrTtlSeconds,
                                         @Nullable Boolean isCacheDisabled,
                                         @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval,
                                         @Nullable GbCacheManager cacheManager,
                                         @Nullable Duration backgroundFetchInterval,
                                         @Nullable FeatureFetchRetryPolicy retryPolicy
    ) {
        this.isCacheDisabled = new AtomicBoolean(Boolean.TRUE.equals(isCacheDisabled));
        if (clientKey == null) {
            throw new IllegalArgumentException("clientKey cannot be null");
        }
        if (backgroundFetchInterval != null && backgroundFetchInterval.isNegative()) {
            throw new IllegalArgumentException("backgroundFetchInterval must not be negative");
        }
        if (apiHost == null) {
            apiHost = "https://cdn.growthbook.io";
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;
        this.featuresEndpoint = apiHost + "/api/features/" + clientKey;
        this.eventsEndpoint = apiHost + "/sub/" + clientKey;
        this.remoteEvalEndPoint = RemoteEvalEndpoints.evalEndpoint(apiHost, clientKey);
        this.requestBodyForRemoteEval = requestBodyForRemoteEval;

        this.encryptionKey = encryptionKey;
        this.swrTtlSeconds = swrTtlSeconds == null ? new AtomicInteger(60) : new AtomicInteger(swrTtlSeconds);
        this.backgroundFetchInterval = backgroundFetchInterval;
        this.retryPolicy = retryPolicy == null ? new FeatureFetchRetryPolicy() : retryPolicy;
        this.featureFetchRetryExecutor = new FeatureFetchRetryExecutor(this.retryPolicy);
        this.featureRefreshScheduler = new FeatureRefreshScheduler();
        this.refreshExpiresAt();
        GbCacheManager resolvedManager = this.isCacheDisabled.get() ? null :
                (cacheManager != null ? cacheManager : determineCacheManager());
        this.cacheManager = new AtomicReference<>(resolvedManager);
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
            if (this.refreshStrategy == FeatureRefreshStrategy.STALE_WHILE_REVALIDATE && isCacheExpired()) {
                this.enqueueFeatureRefreshRequest();
                this.refreshExpiresAt();
            }
            return this.featuresJson.get();
        } finally {
            lock.unlock();
        }
    }

    private void enqueueFeatureRefreshRequest() {
        try {
            refreshFeatures();
        } catch (FeatureFetchException e) {
            log.error("FeatureFetchException occur with message - {}, Code is - {}", e.getMessage(), e.getErrorCode(), e);
        }
    }

    public Map<String, Feature<?>> getFeaturesMap() {
        try {
            lock.lock();
            if (this.refreshStrategy == FeatureRefreshStrategy.STALE_WHILE_REVALIDATE && isCacheExpired()) {
                this.enqueueFeatureRefreshRequest();
                this.refreshExpiresAt();
            }
            return Optional.ofNullable(this.featuresJson.get())
                    .map(TransformationUtil::transformFeatures)
                    .orElse(Collections.emptyMap());
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

    public void fetchFeatures() throws FeatureFetchException {
        refreshFeatures(RefreshMode.DEFAULT);
    }

    public void refreshFeatures() throws FeatureFetchException {
        refreshFeatures(RefreshMode.DEFAULT);
    }

    public void refreshFeatures(RefreshMode refreshMode) throws FeatureFetchException {
        RefreshMode resolvedRefreshMode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        if (shouldSkipRefresh(resolvedRefreshMode)) {
            log.debug("Skipping feature refresh because cached features are newer than the background fetch interval.");
            return;
        }
        fetchFeaturesWithRetries(resolvedRefreshMode);
    }

    public void requestFeatureRefresh(RefreshMode refreshMode) {
        this.featureRefreshScheduler.requestRefresh(refreshMode, this::refreshFeatures);
    }

    private void fetchFeaturesWithRetries(RefreshMode refreshMode) throws FeatureFetchException {
        Optional<FeatureFetchException> failure = this.featureFetchRetryExecutor.execute(() ->
                fetchFeaturesOnce(refreshMode)
        );

        if (failure.isPresent()) {
            handleFetchFailure(failure.get());
        }
    }

    private boolean shouldSkipRefresh(RefreshMode refreshMode) {
        return FeatureRefreshCacheFreshness.shouldSkipRefresh(
                refreshMode == RefreshMode.FORCE,
                this.backgroundFetchInterval,
                this.lastSuccessfulFetchAtMillis::get,
                () -> FeatureRefreshCacheFreshness.timestampMillisOrUnknown(getCacheLastUpdatedMillis()),
                this.hasFeatureData::get,
                this::loadCachedFeaturesIfAvailable
        );
    }

    private void fetchFeaturesOnce(RefreshMode refreshMode) throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(this.featuresEndpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HttpMethods.GET.getMethod());
            if (this.featuresEndpoint.matches(FEATURES_PATH_PATTERN)) {
                if (refreshMode == RefreshMode.FORCE) {
                    connection.setRequestProperty(HttpHeaders.CACHE_CONTROL.getHeader(), "no-cache");
                } else {
                    String cachedEtag = eTagCache.get(this.featuresEndpoint);
                    if (cachedEtag != null) {
                        connection.setRequestProperty(HttpHeaders.IF_NONE_MATCH.getHeader(), cachedEtag);
                    }
                    connection.setRequestProperty(HttpHeaders.CACHE_CONTROL.getHeader(), "max-age=" + this.swrTtlSeconds.get());
                }
            }

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) { // 304
                log.info("Features not modified (304). Using existing data.");
                this.refreshExpiresAt();
                this.onRefreshSuccess(this.featuresJson.get());
                return;
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                if (this.featuresEndpoint.matches(FEATURES_PATH_PATTERN)) {
                    String newEtag = connection.getHeaderField("ETag");
                    if (newEtag != null) {
                        eTagCache.put(this.featuresEndpoint, newEtag);
                    }
                }
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
                return;
            }

            if (FeatureFetchHttpStatus.isRetryable(responseCode)) {
                throw new RetryableFeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                        "responded with status " + responseCode
                );
            }
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                    "responded with status " + responseCode
            );
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RetryableFeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                    e.getMessage(),
                    e
            );
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

    private void handleFetchFailure(FeatureFetchException failure) throws FeatureFetchException {
        FeatureFetchFailureHandler.handle(
                failure,
                this::onRefreshFailed,
                this.hasFeatureData::get,
                this::loadCachedFeaturesIfAvailable
        );
    }

    @Nullable
    private Long getCacheLastUpdatedMillis() {
        GbCacheManager resolvedCacheManager = this.cacheManager == null ? null : this.cacheManager.get();
        if (this.isCacheDisabled.get() || resolvedCacheManager == null) {
            return null;
        }

        try {
            return resolvedCacheManager.getLastUpdatedMillis(FILE_NAME_FOR_CACHE);
        } catch (RuntimeException cacheException) {
            log.warn("Failed to read the feature cache timestamp.", cacheException);
            return null;
        }
    }

    private boolean loadCachedFeaturesIfAvailable() {
        GbCacheManager resolvedCacheManager = this.cacheManager == null ? null : this.cacheManager.get();
        if (this.isCacheDisabled.get() || resolvedCacheManager == null) {
            return false;
        }

        try {
            String cachedData = resolvedCacheManager.loadCache(FILE_NAME_FOR_CACHE);
            if (cachedData == null || cachedData.trim().isEmpty()) {
                return false;
            }
            onResponseJson(cachedData, true);
            return this.hasFeatureData.get();
        } catch (Exception cacheException) {
            log.warn("Failed to load cached features.", cacheException);
            return false;
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

            if (!isFromCache && !isCacheDisabled.get() && cacheManager.get() != null) {
                try { cacheManager.get().saveContent(FILE_NAME_FOR_CACHE, responseJsonString); } catch (RuntimeException ignored) {}
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
                this.hasFeatureData.set(true);
                if (!isFromCache) {
                    this.lastSuccessfulFetchAtMillis.set(System.currentTimeMillis());
                    this.onRefreshSuccess(this.featuresJson.get());
                }
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

    public void onRefreshSuccess(String featuresJson) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            if (callback != null) {
                callback.onRefresh(featuresJson);
            }
        }
    }

    public void onRefreshFailed(Throwable throwable) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            if (callback != null) {
                callback.onError(throwable);
            }
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

        Runnable sseTask = () -> {
            int attempt = 1;
            int maxAttempts = retryPolicy.getMaxAttempts();

            while (attempt <= maxAttempts) {
                BufferedReader reader = null;
                HttpURLConnection connection = null;
                try {
                    connection = establishSseConnection(eventsEndpoint);

                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    StringBuilder dataBuffer = new StringBuilder();
                    String eventType = null;

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(SseKey.DATA.getKey())) {
                            dataBuffer.append(line.substring(QUANTITY_TO_CUT_SSE).trim()).append("\n");
                        } else if (line.startsWith(SseKey.EVENT.getKey())) {
                            eventType = line.substring(SseKey.EVENT.getKey().length()).trim();
                        } else if (line.isEmpty()) {
                            String data = dataBuffer.toString();
                            if (SseEventPayloadValidator.isValidFeaturePayload(eventType, data)) {
                                onResponseJson(data, false);
                            }
                            dataBuffer.setLength(0);
                            eventType = null;
                        }
                    }
                } catch (Exception e) {
                    log.error("SSE connection failed: {}", e.getMessage(), e);
                } finally {
                    try {
                        if (reader != null) reader.close();
                        if (connection != null) connection.disconnect();
                    } catch (IOException e) {
                        log.error("BufferedReader unsuccessfully closed: {}", e.getMessage(), e);
                    }
                }

                if (!Boolean.TRUE.equals(retryOnFailure)) {
                    return;
                }

                if (attempt == maxAttempts) {
                    log.error("SSE connection retries exhausted after {} attempts.", maxAttempts);
                    return;
                }

                int nextAttempt = attempt + 1;
                long delayMillis = retryPolicy.getDelayMillisBeforeAttempt(nextAttempt);
                log.warn(
                        "SSE connection failed. Retry attempt {}/{} in {}ms.",
                        nextAttempt,
                        maxAttempts,
                        delayMillis
                );
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                attempt = nextAttempt;
            }
        };
        Thread sseThread = new Thread(sseTask, "growthbook-sse");
        sseThread.setDaemon(true);
        sseThread.start();
    }

    private void fetchForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        HttpURLConnection urlConnection = null;
        try {
            RequestBodyForRemoteEval payload = requestBodyForRemoteEval == null
                    ? new RequestBodyForRemoteEval()
                    : requestBodyForRemoteEval;
            String body = GrowthBookJsonUtils.getInstance().gson.toJson(payload);

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

    public void shutdown() {
        this.featureRefreshScheduler.shutdown();
    }

    private String getCachedFeatures() throws FeatureFetchException {
        String cachedData = cacheManager == null || cacheManager.get() == null
                ? null
                : cacheManager.get().loadCache(FILE_NAME_FOR_CACHE);
        if (cachedData == null) {
            log.error("FeatureFetchException: No Features from Cache");
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR);
        }
        return cachedData;
    }

    private growthbook.sdk.java.sandbox.GbCacheManager determineCacheManager() {
        try {
            return growthbook.sdk.java.sandbox.CacheManagerFactory.create(
                    growthbook.sdk.java.sandbox.CacheMode.AUTO,
                    null
            );
        } catch (Exception e) {
            return null;
        }
    }
}
