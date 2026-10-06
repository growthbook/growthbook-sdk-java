package growthbook.sdk.java.repository;

import static growthbook.sdk.java.constants.SDKConstants.Endpoints.DEFAULT_API_HOST;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.FEATURES_ENDPOINT_PATH;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.FEATURES_ENDPOINT_PATTERN;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.STREAMING_ENDPOINT_PATH;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.constants.SDKConstants;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.RetryableFeatureFetchException;
import growthbook.sdk.java.featurefetch.FeatureFetchFailureHandler;
import growthbook.sdk.java.featurefetch.FeatureFetchHttpStatus;
import growthbook.sdk.java.featurefetch.FeatureRefreshCacheFreshness;
import growthbook.sdk.java.featurefetch.FeatureRefreshScheduler;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResponseKey;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.remoteeval.RemoteEvalEndpoints;
import growthbook.sdk.java.retry.FeatureFetchRetryExecutor;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.util.DecryptionUtils;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.sse.SseEventPayloadValidator;
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
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final ThreadFactory SSE_RETRY_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "growthbook-sse-retry");
        thread.setDaemon(true);
        return thread;
    };
    private static final ThreadFactory POLL_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "growthbook-feature-poll");
        thread.setDaemon(true);
        return thread;
    };

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
     * Endpoint for SSE request. Built from {@code streamingHost} when supplied,
     * otherwise from {@code apiHost}.
     */
    @Getter
    private final String eventsEndpoint;

    /**
     * Custom headers added to every request against the API host (features GET and
     * remote-eval POST). Values may contain secrets and must never be logged.
     */
    private final Map<String, String> apiHostRequestHeaders;

    /**
     * Custom headers added to the SSE streaming request. Values may contain secrets
     * and must never be logged.
     */
    private final Map<String, String> streamingHostRequestHeaders;

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

    private final AtomicReference<Throwable> lastRefreshError = new AtomicReference<>();

    private final AtomicLong lastRefreshErrorAtMillis = new AtomicLong(0);

    private final AtomicLong refreshSuccessCount = new AtomicLong(0);

    private final AtomicLong refreshFailureCount = new AtomicLong(0);

    private final AtomicLong refreshConsecutiveFailures = new AtomicLong(0);

    private final AtomicLong lastRefreshFailureAtMillis = new AtomicLong(0);

    private final AtomicReference<Boolean> lastRefreshLoadedFromCache = new AtomicReference<>();

    private final AtomicBoolean hasFeatureData = new AtomicBoolean(false);

    /**
     * Seconds after that cache is expired
     */
    @Getter
    private volatile Long expiresAt;

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
     * Optional callbacks for getting updates when features are refreshed.
     * CopyOnWriteArrayList: registration/clearing happens on caller threads while
     * the poll/SSE/retry background threads iterate the list during dispatch.
     */
    private final CopyOnWriteArrayList<FeatureRefreshCallback> refreshCallbacks = new CopyOnWriteArrayList<>();

    /**
     * Flag to know whether GBFeatureRepository is initialized
     */
    @Getter
    private volatile Boolean initialized = false;

    /**
     * Flag to know whether sse connection is allowed
     */
    private volatile Boolean sseAllowed = false;

    @Nullable
    private Request sseRequest = null;

    @Nullable
    private EventSource sseEventSource = null;

    /**
     * The current features payload — raw JSON and parsed forms captured together
     * from one successful refresh and swapped atomically, so readers can never
     * observe features from one payload paired with saved groups from another.
     */
    private final AtomicReference<FeatureSnapshot> snapshot = new AtomicReference<>(FeatureSnapshot.EMPTY);

    /**
     * The current features payload as one immutable snapshot. Prefer this over the
     * individual getters when consuming more than one part of the payload.
     *
     * @return the snapshot from the most recent successful refresh
     */
    public FeatureSnapshot getFeatureSnapshot() {
        return this.snapshot.get();
    }

    /**
     * Allows you to get the saved groups JSON from the provided {@link GBFeaturesRepository#getFeaturesEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your saved groups would not have loaded.
     *
     * @return saved groups JSON string
     */
    @Nullable
    public String getSavedGroupsJson() {
        return this.snapshot.get().getSavedGroupsJson();
    }

    /**
     * Allows you to get the features JSON from the provided {@link GBFeaturesRepository#getFeaturesEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your features would not have loaded.
     *
     * @return feature data JSON in a type of String. Handle refresh strategy
     */
    public String getFeaturesJson() {
        return this.snapshot.get().getFeaturesJson();
    }

    /**
     * Keys are unique identifiers for the features and the values are Feature objects.
     * Feature definitions - To be pulled from API / Cache
     *
     * @return parsed feature definitions
     */
    public Map<String, Feature<?>> getParsedFeatures() {
        return this.snapshot.get().getParsedFeatures();
    }

    public JsonObject getParsedSavedGroups() {
        return this.snapshot.get().getParsedSavedGroups();
    }

    public void setCacheManager(GbCacheManager cacheManager) {
        if (!isCacheDisabled) {
            this.cacheManager = cacheManager;
        } else {
            log.warn("Cache is disabled. Please enable it and set the CacheManager");
        }
    }

    /**
     * CachingManger allows to cache features data to file
     */
    @Getter
    private volatile GbCacheManager cacheManager;

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
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, null, null, isCacheDisabled, null, null);
    }

    /**
     * New constructor that explicitly supports decryptionKey.
     */
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
        this(
                apiHost,
                clientKey,
                encryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                decryptionKey,
                isCacheDisabled,
                requestBodyForRemoteEval,
                cacheManager,
                null,
                null
        );
    }

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
            @Nullable GbCacheManager cacheManager,
            @Nullable Duration backgroundFetchInterval,
            @Nullable FeatureFetchRetryPolicy retryPolicy
    ) {
        this(
                apiHost,
                clientKey,
                encryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                decryptionKey,
                isCacheDisabled,
                requestBodyForRemoteEval,
                cacheManager,
                backgroundFetchInterval,
                retryPolicy,
                null,
                null,
                null
        );
    }

    /**
     * Builder constructor supporting custom request headers and a dedicated streaming host.
     *
     * @param apiHostRequestHeaders       custom headers added to every API host request
     *                                    (features GET and remote-eval POST); reserved SDK headers are ignored
     * @param streamingHost               dedicated host for SSE streaming; falls back to {@code apiHost} when null
     * @param streamingHostRequestHeaders custom headers added to the SSE streaming request;
     *                                    reserved SDK headers are ignored
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
            @Nullable GbCacheManager cacheManager,
            @Nullable Duration backgroundFetchInterval,
            @Nullable FeatureFetchRetryPolicy retryPolicy,
            @Nullable Map<String, String> apiHostRequestHeaders,
            @Nullable String streamingHost,
            @Nullable Map<String, String> streamingHostRequestHeaders
    ) {
        this(apiHost, clientKey, (decryptionKey != null) ? decryptionKey : encryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                isCacheDisabled,
                (requestBodyForRemoteEval != null) ? requestBodyForRemoteEval : new RequestBodyForRemoteEval(),
                cacheManager,
                backgroundFetchInterval,
                retryPolicy,
                apiHostRequestHeaders,
                streamingHost,
                streamingHostRequestHeaders
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
        this(
                apiHost,
                clientKey,
                decryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                isCacheDisabled,
                requestBodyForRemoteEval,
                cacheManager,
                null,
                null
        );
    }

    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Nullable String decryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable OkHttpClient okHttpClient,
            @Nullable Boolean isCacheDisabled,
            @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval,
            @Nullable GbCacheManager cacheManager,
            @Nullable Duration backgroundFetchInterval,
            @Nullable FeatureFetchRetryPolicy retryPolicy
    ) {
        this(
                apiHost,
                clientKey,
                decryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                isCacheDisabled,
                requestBodyForRemoteEval,
                cacheManager,
                backgroundFetchInterval,
                retryPolicy,
                null,
                null,
                null
        );
    }

    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Nullable String decryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable OkHttpClient okHttpClient,
            @Nullable Boolean isCacheDisabled,
            @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval,
            @Nullable GbCacheManager cacheManager,
            @Nullable Duration backgroundFetchInterval,
            @Nullable FeatureFetchRetryPolicy retryPolicy,
            @Nullable Map<String, String> apiHostRequestHeaders,
            @Nullable String streamingHost,
            @Nullable Map<String, String> streamingHostRequestHeaders
    ) {
        this.isCacheDisabled = isCacheDisabled != null && isCacheDisabled; // cache enable by default
        if (clientKey == null) throw new IllegalArgumentException("clientKey cannot be null");
        if (backgroundFetchInterval != null && backgroundFetchInterval.isNegative()) {
            throw new IllegalArgumentException("backgroundFetchInterval must not be negative");
        }

        // Set the defaults when the user does not provide them
        if (apiHost == null) {
            apiHost = DEFAULT_API_HOST;
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;

        // Build the endpoints from the apiHost and clientKey
        this.featuresEndpoint = apiHost + FEATURES_ENDPOINT_PATH + clientKey;
        String streamingHostOrApiHost = (streamingHost == null || streamingHost.trim().isEmpty())
                ? apiHost
                : normalizeStreamingHost(streamingHost);
        this.eventsEndpoint = streamingHostOrApiHost + STREAMING_ENDPOINT_PATH + clientKey;
        this.remoteEvalEndPoint = RemoteEvalEndpoints.evalEndpoint(apiHost, clientKey);
        this.apiHostRequestHeaders = sanitizeCustomHeaders(
                "apiHostRequestHeaders", apiHostRequestHeaders, SDKConstants.RESERVED_REQUEST_HEADERS);
        this.streamingHostRequestHeaders = sanitizeCustomHeaders(
                "streamingHostRequestHeaders", streamingHostRequestHeaders, SDKConstants.RESERVED_STREAMING_REQUEST_HEADERS);

        this.encryptionKey = decryptionKey;
        this.decryptionKey = decryptionKey;

        this.swrTtlSeconds = swrTtlSeconds == null ? SDKConstants.DEFAULT_SWR_TTL_SECONDS : swrTtlSeconds;
        this.backgroundFetchInterval = backgroundFetchInterval;
        this.retryPolicy = retryPolicy == null ? new FeatureFetchRetryPolicy() : retryPolicy;
        this.featureFetchRetryExecutor = new FeatureFetchRetryExecutor(this.retryPolicy);
        this.featureRefreshScheduler = new FeatureRefreshScheduler();
        this.requestBodyForRemoteEval = requestBodyForRemoteEval != null ? requestBodyForRemoteEval : new RequestBodyForRemoteEval();
        this.refreshExpiresAt();

        // Use provided OkHttpClient or create a new one
        if (okHttpClient == null) {
            this.okHttpClient = this.initializeHttpClient();
        } else if (okHttpClient.retryOnConnectionFailure()) {
            log.warn("Disabling OkHttpClient.retryOnConnectionFailure because feature fetch retries are handled by FeatureFetchRetryPolicy.");
            this.okHttpClient = okHttpClient.newBuilder()
                    .retryOnConnectionFailure(false)
                    .build();
        } else {
            // TODO: Check for valid interceptor
            this.okHttpClient = okHttpClient;
        }
        if (!this.isCacheDisabled) {
            this.cacheManager = cacheManager != null ? cacheManager : createCacheManager();
        }
    }

    // Getter for deprecated encryptionKey
    @Deprecated
    @Nullable
    public String getEncryptionKey() {
        return encryptionKey;
    }

    public long getLastSuccessfulFetchAtMillis() {
        return this.lastSuccessfulFetchAtMillis.get();
    }

    @Override
    @Nullable
    public Throwable getLastRefreshError() {
        return this.lastRefreshError.get();
    }

    @Override
    public long getLastRefreshErrorAtMillis() {
        return this.lastRefreshErrorAtMillis.get();
    }

    public boolean hasFeatureData() {
        return this.hasFeatureData.get();
    }

    public boolean isCacheDisabled() {
        return this.isCacheDisabled;
    }

    @Nullable
    public Long getCacheLastUpdatedMillis() {
        return readCacheLastUpdatedMillis();
    }

    public boolean isSseAllowed() {
        return Boolean.TRUE.equals(this.sseAllowed);
    }

    public boolean isSseConnected() {
        return this.sseConnected.get() && !this.shuttingDown.get();
    }

    public int getSseRetryAttempts() {
        return this.sseRetryAttempts.get();
    }

    @Override
    public long getRefreshSuccessCount() {
        return this.refreshSuccessCount.get();
    }

    @Override
    public long getRefreshFailureCount() {
        return this.refreshFailureCount.get();
    }

    @Override
    public long getRefreshConsecutiveFailureCount() {
        return this.refreshConsecutiveFailures.get();
    }

    @Override
    public long getLastRefreshFailureAtMillis() {
        return this.lastRefreshFailureAtMillis.get();
    }

    @Override
    @Nullable
    public Boolean getLastRefreshLoadedFromCache() {
        return this.lastRefreshLoadedFromCache.get();
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
        if (callback == null) {
            return;
        }
        this.refreshCallbacks.addIfAbsent(callback);
    }

    @Override
    public void clearCallbacks() {
        this.refreshCallbacks.clear();
    }

    private GbCacheManager createCacheManager() {
        try {
            return CacheManagerFactory.create(CacheMode.AUTO, null);
        } catch (Exception e) {
            log.warn("Cache Manager creation failed", e);
            return null;
        }
    }

    // Scheduled polling (non-SSE) drives refresh for STALE_WHILE_REVALIDATE strategy
    private ScheduledExecutorService pollScheduler;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private ScheduledExecutorService sseRetryScheduler;
    private final AtomicBoolean sseReconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean sseConnected = new AtomicBoolean(false);
    private final AtomicInteger sseRetryAttempts = new AtomicInteger(0);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private void schedulePolling() {
        if (pollScheduler != null || this.refreshStrategy == FeatureRefreshStrategy.SERVER_SENT_EVENTS) return;
        // Named daemon thread: a non-daemon poller would keep the JVM alive
        // when the application exits without calling shutdown().
        pollScheduler = Executors.newSingleThreadScheduledExecutor(POLL_THREAD_FACTORY);
        pollScheduler.scheduleWithFixedDelay(this::pollOnceSafe, this.swrTtlSeconds, this.swrTtlSeconds, TimeUnit.SECONDS);
    }

    private void pollOnceSafe() {
        if (!polling.compareAndSet(false, true)) return;

        // call the features API.
        long started = System.currentTimeMillis();
        try {
            log.debug("GrowthBook Features Refresh polling starts");
            refreshFeatures();
        } catch (Exception e) {
            log.error("Features Refresh polling failed.", e);
        } finally {
            log.debug("GrowthBook Features Refresh polling ends in ({} ms)", System.currentTimeMillis() - started);
            polling.set(false);
        }
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
                schedulePolling();
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
        this.sseConnected.set(false);
        this.sseEventSource = null;
        this.sseRequest = null;

        if (this.sseHttpClient == null) {
            this.sseHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
                    .retryOnConnectionFailure(false)
                    .connectTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .writeTimeout(0, TimeUnit.SECONDS)
                    .build();
        }

        Request.Builder sseRequestBuilder = new Request.Builder()
                .url(this.eventsEndpoint);
        applyCustomHeaders(sseRequestBuilder, this.streamingHostRequestHeaders);
        applySdkUserAgent(sseRequestBuilder);
        this.sseRequest = sseRequestBuilder
                .header(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.APPLICATION_JSON.getHeader())
                .addHeader(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.SSE_HEADER.getHeader())
                .build();

        GBEventSourceListener gbEventSourceListener =
                new GBEventSourceListener(
                        new GBEventSourceHandler() {
                            @Override
                            public void onClose(EventSource eventSource) {
                                sseConnected.set(false);
                                eventSource.cancel();
                                scheduleSseReconnect(retryOnFailure);
                            }

                            @Override
                            public void onFeaturesResponse(String featuresJsonResponse) throws FeatureFetchException {
                                try {
                                    onResponseJson(featuresJsonResponse, false);
                                    recordRefreshSuccess(false);
                                } catch (FeatureFetchException e) {
                                    recordRefreshError(e);
                                    recordRefreshFailure(false);
                                    throw e;
                                }
                                sseRetryAttempts.set(0);
                                sseReconnectScheduled.set(false);
                            }

                            @Override
                            public void onFeaturesUpdated() {
                                onRefreshSuccess(getFeaturesJson());
                                recordRefreshSuccess(false);
                            }
                        }
                ) {
                    @Override
                    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                        super.onFailure(eventSource, t, response);
                        sseConnected.set(false);
                        eventSource.cancel();
                        scheduleSseReconnect(retryOnFailure);
                    }

                    @Override
                    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                        super.onOpen(eventSource, response);
                        sseConnected.set(true);
                        sseRetryAttempts.set(0);
                        sseReconnectScheduled.set(false);
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

    private synchronized void scheduleSseReconnect(Boolean retryOnFailure) {
        if (!Boolean.TRUE.equals(retryOnFailure)
                || this.shuttingDown.get()
                || !this.sseReconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        int failedConnectionAttempts = this.sseRetryAttempts.incrementAndGet();
        int nextConnectionAttempt = failedConnectionAttempts + 1;
        int maxAttempts = this.retryPolicy.getMaxAttempts();
        if (nextConnectionAttempt > maxAttempts) {
            this.sseRetryAttempts.set(maxAttempts);
            this.sseReconnectScheduled.set(false);
            log.error("SSE connection retries exhausted after {} attempts.", maxAttempts);
            return;
        }

        long delayMillis = this.retryPolicy.getDelayMillisBeforeAttempt(nextConnectionAttempt);
        log.warn(
                "SSE connection failed. Retry attempt {}/{} in {}ms.",
                nextConnectionAttempt,
                maxAttempts,
                delayMillis
        );

        if (this.sseRetryScheduler == null || this.sseRetryScheduler.isShutdown()) {
            this.sseRetryScheduler = Executors.newSingleThreadScheduledExecutor(SSE_RETRY_THREAD_FACTORY);
        }
        this.sseRetryScheduler.schedule(() -> {
            this.sseReconnectScheduled.set(false);
            if (this.shuttingDown.get()) {
                return;
            }

            try {
                refreshFeatures(RefreshMode.FORCE);
            } catch (FeatureFetchException e) {
                log.error("Failed to fetch features while reconnecting SSE.", e);
            }
            createEventSourceListenerAndStartListening(retryOnFailure);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Normalizes a caller-supplied {@code streamingHost} into a value OkHttp can build a request
     * from: a scheme-less host (which {@code OptionsValidator} accepts by assuming {@code https})
     * gets an {@code https://} prefix, and trailing slashes are stripped so the streaming path is
     * not doubled (e.g. {@code host//sub/...}).
     */
    private static String normalizeStreamingHost(String streamingHost) {
        String normalized = streamingHost.trim();
        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Drops entries the SDK cannot honor: blank names, null values, and the reserved
     * SDK-managed header names for this request path ({@code reservedHeaders}).
     * Only header names are ever logged — values may contain secrets.
     */
    private static Map<String, String> sanitizeCustomHeaders(
            String optionName,
            @Nullable Map<String, String> headers,
            Set<String> reservedHeaders
    ) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.trim().isEmpty() || entry.getValue() == null) {
                log.warn("Ignoring {} entry with a null or blank header name or a null value.", optionName);
            } else if (reservedHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                log.warn("Ignoring reserved header '{}' in {}; it is managed by the SDK.", name, optionName);
            } else {
                sanitized.put(name, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    /**
     * Applies custom headers to a request. Called before SDK-managed headers are set,
     * so the SDK values always take priority.
     */
    private static void applyCustomHeaders(Request.Builder requestBuilder, Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sets the SDK User-Agent directly on the request, so it is guaranteed even when a
     * user-supplied {@link OkHttpClient} lacks {@link GBFeaturesRepositoryRequestInterceptor}.
     */
    private static void applySdkUserAgent(Request.Builder requestBuilder) {
        requestBuilder.header(
                GBFeaturesRepositoryRequestInterceptor.USER_AGENT_HEADER,
                GBFeaturesRepositoryRequestInterceptor.USER_AGENT_VALUE
        );
    }

    /**
     * @return A new {@link OkHttpClient} with an interceptor {@link GBFeaturesRepositoryRequestInterceptor}
     */
    private OkHttpClient initializeHttpClient() {

        return new OkHttpClient.Builder()
                .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
                .retryOnConnectionFailure(false)
                .build();
    }

    private void refreshExpiresAt() {
        this.expiresAt = Instant.now().getEpochSecond() + this.swrTtlSeconds;
    }

    private Boolean isCacheExpired() {
        long now = Instant.now().getEpochSecond();
        return now >= this.expiresAt;
    }

    private boolean shouldSkipRefresh(RefreshMode refreshMode) {
        return FeatureRefreshCacheFreshness.shouldSkipRefresh(
                refreshMode == RefreshMode.FORCE,
                this.backgroundFetchInterval,
                this.lastSuccessfulFetchAtMillis::get,
                () -> FeatureRefreshCacheFreshness.timestampMillisOrUnknown(readCacheLastUpdatedMillis()),
                this.hasFeatureData::get,
                this::loadCachedFeaturesIfAvailable
        );
    }

    /**
     * Performs a network request to fetch the features from the GrowthBook API
     * with the provided endpoint.
     * If an encryptionKey is provided, it is assumed the features endpoint is using encrypted features.
     * This method will attempt to decrypt the encrypted features with the provided encryptionKey.
     */
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

    private void fetchFeaturesOnce(RefreshMode refreshMode) throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(this.featuresEndpoint);
        applyCustomHeaders(requestBuilder, this.apiHostRequestHeaders);
        applySdkUserAgent(requestBuilder);

        if (this.featuresEndpoint.matches(FEATURES_ENDPOINT_PATTERN)) {
            if (refreshMode == RefreshMode.FORCE) {
                requestBuilder.header(HttpHeaders.CACHE_CONTROL.getHeader(), "no-cache");
            } else {
                String cacheETag = eTagCache.get(this.featuresEndpoint);
                if (cacheETag != null) {
                    requestBuilder.addHeader(HttpHeaders.IF_NONE_MATCH.getHeader(), cacheETag);
                }
                requestBuilder.header(HttpHeaders.CACHE_CONTROL.getHeader(), "max-age=" + this.swrTtlSeconds);
            }
        }
        Request request = requestBuilder.build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            String sseSupportHeader = response.header(HttpHeaders.X_SSE_SUPPORT.getHeader());
            this.sseAllowed = Objects.equals(sseSupportHeader, ENABLED);

            this.onSuccess(response);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RetryableFeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                    e.getMessage(),
                    e
            );
        }
    }

    private void handleFetchFailure(FeatureFetchException failure) throws FeatureFetchException {
        boolean hadFeatureData = this.hasFeatureData.get();
        recordRefreshError(failure);
        FeatureFetchFailureHandler.handle(
                failure,
                this::onRefreshFailed,
                this.hasFeatureData::get,
                this::loadCachedFeaturesIfAvailable
        );
        boolean loadedFromCache = !hadFeatureData && this.hasFeatureData.get();
        recordRefreshFailure(loadedFromCache);
    }

    @Nullable
    private Long readCacheLastUpdatedMillis() {
        if (this.isCacheDisabled || this.cacheManager == null) {
            return null;
        }

        try {
            return this.cacheManager.getLastUpdatedMillis(FILE_NAME);
        } catch (RuntimeException cacheException) {
            log.warn("Failed to read the feature cache timestamp.", cacheException);
            return null;

        }
    }

    private boolean loadCachedFeaturesIfAvailable() {
        if (this.isCacheDisabled || this.cacheManager == null) {
            return false;
        }

        try {
            String cachedData = this.cacheManager.loadCache(FILE_NAME);
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

    private void recordRefreshError(Throwable throwable) {
        Throwable error = throwable == null
                ? new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN)
                : throwable;
        this.lastRefreshError.set(error);
        this.lastRefreshErrorAtMillis.set(System.currentTimeMillis());
    }

    private void recordRefreshSuccess(boolean loadedFromCache) {
        this.refreshSuccessCount.incrementAndGet();
        this.refreshConsecutiveFailures.set(0);
        this.lastRefreshLoadedFromCache.set(loadedFromCache);
    }

    private void recordRefreshFailure(boolean loadedFromCache) {
        this.refreshFailureCount.incrementAndGet();
        this.refreshConsecutiveFailures.incrementAndGet();
        this.lastRefreshFailureAtMillis.set(System.currentTimeMillis());
        this.lastRefreshLoadedFromCache.set(loadedFromCache);
    }

    /**
     * Reads the response JSON properties `features` or `encryptedFeatures`, and decrypts if necessary
     *
     * @param responseJsonString JSON response object
     */
    private void onResponseJson(String responseJsonString, boolean isFromCache) throws FeatureFetchException {
        try {
            JsonObject jsonObject = parseResponse(responseJsonString);

            RefreshedPayload payload = this.decryptionKey != null
                    ? extractEncryptedPayload(jsonObject)
                    : extractUnencryptedPayload(jsonObject);

            // Cache only a response we successfully parsed and extracted, so a bad body
            // (empty, "null", malformed, or missing features) can't overwrite a good
            // cached payload that the startup fallback relies on.
            if (!isFromCache) {
                saveToCacheQuietly(responseJsonString);
            }

            applyPayload(payload, isFromCache);
        } catch (DecryptionUtils.DecryptionException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}",
                    e.getMessage(), e);

            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    e.getMessage()
            );
        }
    }

    /**
     * Persist the raw response to the cache, swallowing any cache errors so they never
     * disrupt feature processing. No-op when caching is disabled or unconfigured.
     */
    private void saveToCacheQuietly(String responseJsonString) {
        if (isCacheDisabled || cacheManager == null) {
            return;
        }
        try {
            cacheManager.saveContent(FILE_NAME, responseJsonString);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Parse the response body into a {@link JsonObject}, surfacing the SDK's declared
     * {@link FeatureFetchException} instead of an unchecked exception when the body is not
     * a JSON object. Gson returns {@code null} for an empty or whitespace-only body, and
     * throws {@link JsonSyntaxException} for the literal {@code "null"}, a JSON array, an
     * HTML error page, or truncated/malformed JSON.
     */
    private JsonObject parseResponse(String responseJsonString) throws FeatureFetchException {
        JsonObject jsonObject;
        try {
            jsonObject = GrowthBookJsonUtils.getInstance()
                    .gson.fromJson(responseJsonString, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw configurationError("features response is not a JSON object: " + e.getMessage());
        }

        if (jsonObject == null) {
            throw configurationError("empty or invalid features response");
        }

        return jsonObject;
    }

    /**
     * Extract and decrypt the encrypted features/saved-groups payload.
     */
    private RefreshedPayload extractEncryptedPayload(JsonObject jsonObject) throws FeatureFetchException,
            DecryptionUtils.DecryptionException {
        JsonElement encryptedFeaturesJsonElement = jsonObject.get(FeatureResponseKey.ENCRYPTED_FEATURES_KEY.getKey());
        JsonElement encryptedSavedGroupsJsonElement = jsonObject.get(FeatureResponseKey.ENCRYPTED_SAVED_GROUPS_KEY.getKey());

        // A missing, null, or non-string encryptedFeatures value means the endpoint isn't
        // encrypted as configured. Guard before getAsString(), which would otherwise throw
        // an unchecked UnsupportedOperationException on a JsonNull.
        if (!isJsonString(encryptedFeaturesJsonElement)) {
            throw configurationError("encryptionKey provided but endpoint not encrypted");
        }

        String refreshedSavedGroups = "";
        if (isJsonString(encryptedSavedGroupsJsonElement)) {
            refreshedSavedGroups = DecryptionUtils
                    .decrypt(encryptedSavedGroupsJsonElement.getAsString(), this.decryptionKey).trim();
        }

        String refreshedFeatures = DecryptionUtils
                .decrypt(encryptedFeaturesJsonElement.getAsString(), this.decryptionKey).trim();

        // The decrypted payload must itself be a features JSON object.
        if (!isJsonObjectString(refreshedFeatures)) {
            throw configurationError("decrypted features payload is not a JSON object");
        }

        return new RefreshedPayload(refreshedFeatures, refreshedSavedGroups);
    }

    /**
     * Extract the unencrypted features/saved-groups payload.
     */
    private RefreshedPayload extractUnencryptedPayload(JsonObject jsonObject) throws FeatureFetchException {
        JsonElement featuresJsonElement = jsonObject.get(FeatureResponseKey.FEATURE_KEY.getKey());
        JsonElement savedGroupsJsonElement = jsonObject.get(FeatureResponseKey.SAVED_GROUP_KEY.getKey());

        // A missing key, an explicit JSON null (e.g. {"features":null}), or any non-object
        // value all mean there are no usable features. Treat them alike so a null-features
        // response isn't silently counted as a successful refresh that wipes all flags.
        if (featuresJsonElement == null || !featuresJsonElement.isJsonObject()) {
            throw configurationError("No features found");
        }

        String refreshedSavedGroups = (savedGroupsJsonElement != null && savedGroupsJsonElement.isJsonObject())
                ? savedGroupsJsonElement.toString().trim()
                : "";

        return new RefreshedPayload(featuresJsonElement.toString().trim(), refreshedSavedGroups);
    }

    /**
     * Store the refreshed payload, transform it, and notify refresh listeners.
     */
    private void applyPayload(RefreshedPayload payload, boolean isFromCache) {
        Map<String, Feature<?>> newParsed = TransformationUtil.transformFeatures(payload.features);
        JsonObject newSaved = TransformationUtil.transformSavedGroups(payload.savedGroups);
        // One atomic swap: readers never see this payload's features paired
        // with a previous payload's saved groups (or vice versa).
        this.snapshot.set(new FeatureSnapshot(
                payload.features,
                payload.savedGroups,
                newParsed == null ? Collections.emptyMap() : newParsed,
                newSaved == null ? new JsonObject() : newSaved
        ));
        this.hasFeatureData.set(true);

        if (!isFromCache) {
            this.lastSuccessfulFetchAtMillis.set(System.currentTimeMillis());
            this.onRefreshSuccess(payload.features);
        }
        // bump TTL only after successful processing
        this.refreshExpiresAt();
    }

    /**
     * @return {@code true} if the element is present and a JSON string primitive.
     */
    private static boolean isJsonString(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    /**
     * @return {@code true} if the given text parses to a JSON object.
     */
    private boolean isJsonObjectString(@Nullable String json) {
        if (json == null) {
            return false;
        }
        try {
            JsonElement parsed = GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonElement.class);
            return parsed != null && parsed.isJsonObject();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * Build a logged {@link FeatureFetchException} with the CONFIGURATION_ERROR code.
     */
    private FeatureFetchException configurationError(String message) {
        log.error("FeatureFetchException: CONFIGURATION_ERROR feature fetch error code: {}", message);
        return new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                message
        );
    }

    /**
     * Immutable holder for the features and saved-groups JSON extracted from a response.
     */
    private static final class RefreshedPayload {
        private final String features;
        private final String savedGroups;

        private RefreshedPayload(String features, String savedGroups) {
            this.features = features;
            this.savedGroups = savedGroups;
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

    public int getActiveFeatureCount() {
        return this.snapshot.get().getParsedFeatures().size();
    }

    /**
     * Handles the successful features fetching response
     *
     * @param response Successful response
     */
    private void onSuccess(Response response) throws FeatureFetchException {
        try {
            ResponseBody responseBody = response.body();

            if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                log.info("Features not modified (304). Using existing data.");
                this.refreshExpiresAt();
                this.onRefreshSuccess(getFeaturesJson());
                recordRefreshSuccess(false);
                return;
            }

            if (response.code() != HttpURLConnection.HTTP_OK) {
                String responseText = responseBody != null ? responseBody.string() : "null";
                log.error(
                        "FeatureFetchException: HTTP_RESPONSE_ERROR with status {}, response: {}",
                        response.code(),
                        responseText
                );

                if (FeatureFetchHttpStatus.isRetryable(response.code())) {
                    throw new RetryableFeatureFetchException(
                            FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                            "responded with status " + response.code()
                    );
                }
                throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                        "responded with status " + response.code()
                );
            }

            if (responseBody == null) {
                throw new RetryableFeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                        "Feature response body was null"
                );
            }

            if (response.code() == HttpURLConnection.HTTP_OK && this.featuresEndpoint.matches(FEATURES_ENDPOINT_PATTERN)) {
                String newETag = response.header("ETag");
                if (newETag != null) {
                    eTagCache.put(this.featuresEndpoint, newETag);
                }
            }

            onResponseJson(responseBody.string(), false);
            recordRefreshSuccess(false);

        } catch (IOException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}", e.getMessage(), e);
            throw new RetryableFeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    e.getMessage(),
                    e
            );
        }
    }

    private interface GBEventSourceHandler {
        void onClose(EventSource eventSource);

        void onFeaturesResponse(String featuresJsonResponse) throws FeatureFetchException;

        void onFeaturesUpdated() throws FeatureFetchException;
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
            // Heartbeat/keepalive events carry no feature changes; ignore them so they neither
            // trigger a refresh nor get parsed as a feature payload.
            if (SseEventPayloadValidator.isHeartbeatEvent(type)) {
                return;
            }

            try {
                if (data.trim().isEmpty()) {
                    handler.onFeaturesUpdated();
                    return;
                }
                handler.onFeaturesResponse(data);
            } catch (FeatureFetchException e) {
                log.error("Failed to process SSE feature payload: {}", e.getMessage(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error while processing SSE feature payload.", e);
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
        this.shuttingDown.set(true);
        this.sseConnected.set(false);
        this.featureRefreshScheduler.shutdown();
        // stop polling
        if (this.pollScheduler != null) {
            this.pollScheduler.shutdownNow();
            this.pollScheduler = null;
            log.info("Polling scheduler shut down");
        }
        if (this.sseRetryScheduler != null) {
            this.sseRetryScheduler.shutdownNow();
            this.sseRetryScheduler = null;
            log.info("SSE retry scheduler shut down");
        }
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
            if (this.cacheManager != null) {
                try {
                    this.cacheManager.clearCache();
                } catch (Exception ignored) {
                }
                this.cacheManager = null;
                log.info("CacheManager shutdown");
            }

        }
    }

    public void fetchForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        if (this.remoteEvalEndPoint == null) {
            throw new IllegalArgumentException("remote eval features endpoint cannot be null");
        }
        RequestBodyForRemoteEval payload = requestBodyForRemoteEval == null
                ? new RequestBodyForRemoteEval()
                : requestBodyForRemoteEval;
        String jsonBody = GrowthBookJsonUtils.getInstance().gson.toJson(payload);
        RequestBody requestBody = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
        );
        Request.Builder remoteEvalRequestBuilder = new Request.Builder()
                .url(this.remoteEvalEndPoint)
                .post(requestBody);
        applyCustomHeaders(remoteEvalRequestBuilder, this.apiHostRequestHeaders);
        applySdkUserAgent(remoteEvalRequestBuilder);
        Request request = remoteEvalRequestBuilder.build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.code() == 200) {
                onSuccess(response);
            } else {
                Throwable error = new Throwable("Response is not success, response code is:" + response.code() + ". And message is: " + response.message());
                recordRefreshError(error);
                recordRefreshFailure(false);
                onRefreshFailed(error);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            FeatureFetchException fetchException = new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, e.getMessage());
            recordRefreshError(fetchException);
            recordRefreshFailure(false);
            throw fetchException;
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
