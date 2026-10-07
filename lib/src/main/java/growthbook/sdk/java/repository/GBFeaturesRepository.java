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
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.FeatureResponseKey;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.remoteeval.RemoteEvalEndpoints;
import growthbook.sdk.java.repository.internal.FeatureRefreshNotifier;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
     * Optional inline bootstrap payload, in the same shape as the features endpoint response
     * ({@code {"features": {...}, "savedGroups": {...}}}, or {@code encryptedFeatures} when a
     * {@link #decryptionKey} is set). When present, the repository seeds its state from this payload
     * before the first network refresh, enabling an instant offline cold start. The payload is only a
     * bridge: the first successful network refresh replaces it.
     */
    @Nullable
    @Getter
    private final String initialPayload;

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
     * Legacy callbacks for getting updates when features are refreshed.
     * CopyOnWriteArrayList: registration/clearing happens on caller threads while
     * the poll/SSE/retry background threads iterate the list during dispatch.
     *
     * @deprecated Use {@link #addFeatureRefreshListener(FeatureRefreshListener)}.
     */
    @Deprecated
    private final CopyOnWriteArrayList<FeatureRefreshCallback> refreshCallbacks = new CopyOnWriteArrayList<>();

    /**
     * Metadata-only listeners used by the feature refresh listener API.
     */
    private final FeatureRefreshNotifier featureRefreshNotifier = new FeatureRefreshNotifier(this::getActiveFeatureCount, () -> this.refreshStrategy);

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
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, okHttpClient, decryptionKey,
                isCacheDisabled, requestBodyForRemoteEval, cacheManager, backgroundFetchInterval, retryPolicy, null);
    }

    /**
     * Builder constructor supporting an inline bootstrap payload.
     *
     * @param initialPayload optional inline feature payload used to bootstrap the repository at cold
     *                       start before the first network fetch
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
            @Nullable String initialPayload
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
                initialPayload
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
        this(apiHost, clientKey, decryptionKey, refreshStrategy, swrTtlSeconds, okHttpClient,
                isCacheDisabled, requestBodyForRemoteEval, cacheManager, backgroundFetchInterval, retryPolicy, null);
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
            @Nullable String initialPayload
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
        this.eventsEndpoint = apiHost + STREAMING_ENDPOINT_PATH + clientKey;
        this.remoteEvalEndPoint = RemoteEvalEndpoints.evalEndpoint(apiHost, clientKey);

        this.encryptionKey = decryptionKey;
        this.decryptionKey = decryptionKey;
        this.initialPayload = validateInitialPayload(initialPayload);

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
     * @deprecated Use {@link #addFeatureRefreshListener(FeatureRefreshListener)}.
     */
    @Deprecated
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        if (callback == null) {
            return;
        }
        this.refreshCallbacks.addIfAbsent(callback);
    }

    /**
     * Registers a listener notified after every refresh attempt, successful or failed.
     *
     * <p><b>Threading:</b> repository-level listeners run <em>synchronously on the thread that
     * performed the refresh</em> — the polling scheduler, the SSE event thread, or whichever thread
     * called {@link #refreshFeatures()}. A slow listener therefore delays the next refresh, and a
     * listener that blocks stalls feature updates entirely. Keep the callback short, or hand the
     * event off to your own executor.
     *
     * <p>Listeners registered through
     * {@code GrowthBookClient.addFeatureRefreshListener(FeatureRefreshListener)} do not have this
     * constraint: the client dispatches them on a dedicated daemon thread (or on the executor
     * supplied via {@code Options.featureRefreshListenerExecutor}).
     *
     * <p>A listener that throws is logged and skipped; the remaining listeners still run.
     *
     * @param listener listener to register; {@code null} is ignored
     */
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        if (listener != null) {
            this.featureRefreshNotifier.add(listener);
        }
    }

    public void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        if (listener != null) {
            this.featureRefreshNotifier.remove(listener);
        }
    }

    /**
     * @deprecated Use listener-specific unsubscription with
     * {@link #removeFeatureRefreshListener(FeatureRefreshListener)} where available.
     */
    @Deprecated
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

    /**
     * Runs the first post-seed refresh off the caller's thread so a seeded {@code initialize()} returns
     * immediately (instant cold start). Uses {@link RefreshMode#FORCE} so the seed's in-memory data does
     * not suppress the refresh; a failure is swallowed, leaving the seed in place.
     */
    private void scheduleImmediateBackgroundRefresh() {
        ScheduledExecutorService scheduler = this.pollScheduler;
        if (scheduler == null) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                refreshFeatures(RefreshMode.FORCE, FeatureRefreshSource.INITIALIZATION);
            } catch (Exception e) {
                log.debug("Background initial refresh after seeding failed; continuing to serve the seeded payload.", e);
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void pollOnceSafe() {
        if (!polling.compareAndSet(false, true)) return;

        // call the features API.
        long started = System.currentTimeMillis();
        try {
            log.debug("GrowthBook Features Refresh polling starts");
            refreshFeatures(RefreshMode.DEFAULT, FeatureRefreshSource.POLLING);
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

        seedInitialPayload();
        boolean seeded = this.initialPayload != null;
        switch (this.refreshStrategy) {
            case STALE_WHILE_REVALIDATE:
                if (seeded) {
                    // Instant cold start: the seed already serves evaluations, so run the first refresh
                    // in the background instead of blocking initialize() on a possibly slow/unreachable
                    // network call. FORCE so the seed cannot borrow cache freshness and suppress it.
                    schedulePolling();
                    scheduleImmediateBackgroundRefresh();
                } else {
                    refreshFeatures(RefreshMode.DEFAULT, FeatureRefreshSource.INITIALIZATION);
                    schedulePolling();
                }
                break;

            case SERVER_SENT_EVENTS:
                // FORCE when seeded so the initial fetch is not skipped by the seed's freshness.
                refreshFeatures(
                        seeded ? RefreshMode.FORCE : RefreshMode.DEFAULT,
                        FeatureRefreshSource.INITIALIZATION);
                initializeSSE(retryOnFailure);
                break;

            case REMOTE_EVAL_STRATEGY:
                fetchForRemoteEval(this.requestBodyForRemoteEval, FeatureRefreshSource.REMOTE_EVALUATION);
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
                                sseConnected.set(false);
                                eventSource.cancel();
                                scheduleSseReconnect(retryOnFailure);
                            }

                            @Override
                            public void onFeaturesResponse(String featuresJsonResponse) throws FeatureFetchException {
                                refreshFromJson(featuresJsonResponse, FeatureRefreshSource.SSE);
                                sseRetryAttempts.set(0);
                                sseReconnectScheduled.set(false);
                            }

                            @Override
                            public void onFeaturesUpdated() {
                                onFeaturesUpdatedSignal();
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
        refreshFeatures(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
    }

    public void refreshFeatures() throws FeatureFetchException {
        refreshFeatures(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
    }

    public void refreshFeatures(RefreshMode refreshMode) throws FeatureFetchException {
        refreshFeatures(refreshMode, FeatureRefreshSource.MANUAL);
    }

    private void refreshFeatures(RefreshMode refreshMode, FeatureRefreshSource source) throws FeatureFetchException {
        RefreshMode resolvedRefreshMode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        if (shouldSkipRefresh(resolvedRefreshMode)) {
            log.debug("Skipping feature refresh because cached features are newer than the background fetch interval.");
            return;
        }
        fetchFeaturesWithRetries(resolvedRefreshMode, source);
    }

    public void requestFeatureRefresh(RefreshMode refreshMode) {
        this.featureRefreshScheduler.requestRefresh(refreshMode, this::refreshFeatures);
    }

    private void fetchFeaturesWithRetries(RefreshMode refreshMode, FeatureRefreshSource source) throws FeatureFetchException {
        long startedAtNanos = System.nanoTime();
        Optional<FeatureFetchException> failure = this.featureFetchRetryExecutor.execute(() ->
                fetchFeaturesOnce(refreshMode, source, startedAtNanos)
        );

        if (failure.isPresent()) {
            handleFetchFailure(failure.get(), source, startedAtNanos);
        }
    }

    private void fetchFeaturesOnce(
            RefreshMode refreshMode,
            FeatureRefreshSource source,
            long startedAtNanos
    ) throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(this.featuresEndpoint);

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

            this.onSuccess(response, source, startedAtNanos);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RetryableFeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                    e.getMessage(),
                    e
            );
        }
    }

    private void handleFetchFailure(
            FeatureFetchException failure,
            FeatureRefreshSource source,
            long startedAtNanos
    ) throws FeatureFetchException {
        boolean hadFeatureData = this.hasFeatureData.get();
        Throwable eventError = failure.getCause() == null ? failure : failure.getCause();
        recordRefreshError(failure);
        // Whether the cached payload actually changed the held snapshot, which is not the same
        // question as whether a payload was loaded: a cache holding the same definitions as the
        // starting snapshot loads successfully while changing nothing.
        AtomicBoolean cachedFeaturesChanged = new AtomicBoolean(false);
        FeatureFetchFailureHandler.handle(
                failure,
                this::onRefreshFailed,
                this.hasFeatureData::get,
                () -> loadCachedFeaturesIfAvailable(cachedFeaturesChanged)
        );
        boolean loadedFromCache = !hadFeatureData && this.hasFeatureData.get();
        recordRefreshFailure(loadedFromCache);
        this.featureRefreshNotifier.notifyFailure(
                eventError,
                source,
                loadedFromCache && cachedFeaturesChanged.get(),
                loadedFromCache,
                FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
        );
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
        return loadCachedFeaturesIfAvailable(new AtomicBoolean());
    }

    /**
     * Loads the cached payload when one is available.
     *
     * @param featuresChangedOut set to whether the cached payload differs from the snapshot already
     *                           held. {@code onResponseJson} computes this, and discarding it would
     *                           force callers to infer "changed" from "loaded" — which is wrong for
     *                           a cache whose definitions match the current snapshot.
     * @return whether feature data is available after the attempt
     */
    private boolean loadCachedFeaturesIfAvailable(AtomicBoolean featuresChangedOut) {
        if (this.isCacheDisabled || this.cacheManager == null) {
            return false;
        }

        try {
            String cachedData = this.cacheManager.loadCache(FILE_NAME);
            if (cachedData == null || cachedData.trim().isEmpty()) {
                return false;
            }
            featuresChangedOut.set(onResponseJson(cachedData, true));
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
     * Validates the inline bootstrap payload at construction time so a malformed payload fails fast
     * instead of surfacing later as an opaque fetch failure. Only structural JSON validity is checked
     * here; key presence and decryption are enforced when the payload is seeded in {@link #initialize()}.
     *
     * @param payload the raw inline payload, may be {@code null} or blank
     * @return the payload unchanged, or {@code null} when blank
     * @throws IllegalArgumentException when the payload is not a valid JSON object
     */
    @Nullable
    private static String validateInitialPayload(@Nullable String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement parsed = GrowthBookJsonUtils.getInstance().gson.fromJson(payload, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("initialPayload must be a JSON object");
            }
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("initialPayload is not valid JSON: " + e.getMessage(), e);
        }
        return payload;
    }

    /**
     * Seeds repository state from the inline bootstrap payload, if one was supplied. Reuses the
     * cache-load path ({@code isFromCache=true}) so the seed does not write to the file cache and does
     * not advance {@code lastSuccessfulFetchAtMillis} — the payload is a bridge, not a network fetch.
     * The seed is reported to listeners/metrics with {@link FeatureRefreshSource#INITIAL_PAYLOAD} and
     * {@code loadedFromCache=true} so it is never mistaken for a successful network refresh.
     *
     * @throws FeatureFetchException when the payload is missing required keys or cannot be decrypted;
     *         seeding fails fast rather than silently falling through to the network.
     */
    private void seedInitialPayload() throws FeatureFetchException {
        if (this.initialPayload == null) {
            return;
        }
        long startedAtNanos = System.nanoTime();
        boolean featuresChanged = onResponseJson(this.initialPayload, true);
        this.featureRefreshNotifier.notifySuccess(
                FeatureRefreshSource.INITIAL_PAYLOAD,
                featuresChanged,
                true,
                FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
        );
    }

    /**
     * Reads the response JSON properties `features` or `encryptedFeatures`, and decrypts if necessary
     *
     * @param responseJsonString JSON response object
     */
    private boolean onResponseJson(String responseJsonString, boolean isFromCache) throws FeatureFetchException {
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

            return applyPayload(payload, isFromCache);
        } catch (DecryptionUtils.DecryptionException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}",
                    e.getMessage(), e);

            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    e.getMessage()
            );
        }
    }

    private void refreshFromJson(String responseJsonString, FeatureRefreshSource source) throws FeatureFetchException {
        long startedAtNanos = System.nanoTime();
        try {
            boolean featuresChanged = onResponseJson(responseJsonString, false);
            recordRefreshSuccess(false);
            this.featureRefreshNotifier.notifySuccess(
                    source,
                    featuresChanged,
                    false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
            );
        } catch (FeatureFetchException e) {
            recordRefreshError(e);
            recordRefreshFailure(false);
            this.featureRefreshNotifier.notifyFailure(
                    e,
                    source,
                    false,
                    false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
            );
            throw e;
        }
    }

    /**
     * Handles an SSE event that signals features changed server-side but carries no payload
     * (empty {@code data}). Notifies both the legacy {@link FeatureRefreshCallback} channel and
     * the modern {@link FeatureRefreshListener} channel, so subscribers that only observe
     * listeners — in particular the remote-eval cache invalidation — are triggered. Routing this
     * solely through {@link #onRefreshSuccess} would leave such listeners unnotified.
     */
    private void onFeaturesUpdatedSignal() {
        onRefreshSuccess(getFeaturesJson());
        recordRefreshSuccess(false);
        this.featureRefreshNotifier.notifySuccess(FeatureRefreshSource.SSE, false, false, 0L);
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
     *
     * @return {@code true} when the payload differs from the previously held snapshot
     */
    private boolean applyPayload(RefreshedPayload payload, boolean isFromCache) {
        FeatureSnapshot previous = this.snapshot.get();
        boolean featuresChanged = !Objects.equals(previous.getFeaturesJson(), payload.features)
                || !Objects.equals(previous.getSavedGroupsJson(), payload.savedGroups);

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
        return featuresChanged;
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
                try {
                    callback.onRefresh(featuresJson);
                } catch (Exception e) {
                    log.warn("Feature refresh callback failed", e);
                }
            }
        }
    }

    private void onRefreshFailed(Throwable throwable) {
        for (FeatureRefreshCallback callback : this.refreshCallbacks) {
            if (callback != null) {
                try {
                    callback.onError(throwable);
                } catch (Exception e) {
                    log.warn("Feature refresh error callback failed", e);
                }
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
    private void onSuccess(
            Response response,
            FeatureRefreshSource source,
            long startedAtNanos
    ) throws FeatureFetchException {
        try {
            ResponseBody responseBody = response.body();

            if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                log.info("Features not modified (304). Using existing data.");
                this.refreshExpiresAt();
                this.onRefreshSuccess(getFeaturesJson());
                recordRefreshSuccess(false);
                this.featureRefreshNotifier.notifySuccess(
                        source,
                        false,
                        false,
                        FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
                );
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

            boolean featuresChanged = onResponseJson(responseBody.string(), false);
            recordRefreshSuccess(false);
            this.featureRefreshNotifier.notifySuccess(
                    source,
                    featuresChanged,
                    false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
            );

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
            if (SseEventPayloadValidator.isHeartbeatEvent(type)) {
                return;
            }

            try {
                if (SseEventPayloadValidator.isValidFeaturePayload(type, data)) {
                    handler.onFeaturesResponse(data);
                } else {
                    handler.onFeaturesUpdated();
                }
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
        this.refreshCallbacks.clear();
        this.featureRefreshNotifier.clear();
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
        fetchForRemoteEval(requestBodyForRemoteEval, FeatureRefreshSource.REMOTE_EVALUATION);
    }

    private void fetchForRemoteEval(
            RequestBodyForRemoteEval requestBodyForRemoteEval,
            FeatureRefreshSource source
    ) throws FeatureFetchException {
        if (this.remoteEvalEndPoint == null) {
            throw new IllegalArgumentException("remote eval features endpoint cannot be null");
        }
        RequestBodyForRemoteEval payload = requestBodyForRemoteEval == null
                ? new RequestBodyForRemoteEval()
                : requestBodyForRemoteEval;
        long startedAtNanos = System.nanoTime();
        String jsonBody = GrowthBookJsonUtils.getInstance().gson.toJson(payload);
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
                onSuccess(response, source, startedAtNanos);
            } else {
                Throwable error = new Throwable("Response is not success, response code is:" + response.code() + ". And message is: " + response.message());
                recordRefreshError(error);
                recordRefreshFailure(false);
                onRefreshFailed(error);
                featureRefreshNotifier.notifyFailure(
                        error,
                        source,
                        false,
                        false,
                        FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
                );
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            FeatureFetchException fetchException = new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, e.getMessage());
            recordRefreshError(fetchException);
            recordRefreshFailure(false);
            featureRefreshNotifier.notifyFailure(
                    fetchException,
                    source,
                    false,
                    false,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos)
            );
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
