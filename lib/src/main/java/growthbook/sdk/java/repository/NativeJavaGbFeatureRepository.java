package growthbook.sdk.java.repository;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.constants.SDKConstants;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.repository.internal.FeatureCacheStore;
import growthbook.sdk.java.repository.internal.FeatureEndpoints;
import growthbook.sdk.java.repository.internal.FeatureRefreshEngine;
import growthbook.sdk.java.repository.internal.UrlConnectionFeatureTransport;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided.
 * Initialize with {@link NativeJavaGbFeatureRepository#initialize()}
 * Get the features JSON with {@link NativeJavaGbFeatureRepository#getFeaturesJson()}.
 * Get the savedGroups JSON with {@link NativeJavaGbFeatureRepository#getSavedGroupsJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 *
 * <p>Fetches over {@link java.net.HttpURLConnection}, with no HTTP library. Under
 * {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} there is no background thread: reading
 * features after the TTL expired refreshes them on the reading thread. Shared across threads; the
 * refresh logic lives in {@link FeatureRefreshEngine}.
 */
public class NativeJavaGbFeatureRepository implements IGBFeaturesRepository {

    public static final String FILE_PATH_FOR_CACHE = "src/main/resources";

    public static final String EMPTY_JSON_OBJECT_STRING = "{}";

    private final FeatureRefreshEngine engine;

    private final FeatureEndpoints endpoints;

    @Nullable
    private final String encryptionKey;

    /**
     * -- GETTER --
     *  The standard cache TTL to use (60 seconds)
     */
    @Getter
    private final AtomicInteger swrTtlSeconds;

    @Nullable
    private final Duration backgroundFetchInterval;

    /**
     * -- GETTER --
     *  Bounded
     *  retry policy for feature fetch requests.
     */
    @Getter
    private final FeatureFetchRetryPolicy retryPolicy;

    @Nullable
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
        if (backgroundFetchInterval != null && backgroundFetchInterval.isNegative()) {
            throw new IllegalArgumentException("backgroundFetchInterval must not be negative");
        }
        this.endpoints = FeatureEndpoints.of(apiHost, clientKey);
        this.encryptionKey = encryptionKey;
        this.swrTtlSeconds = new AtomicInteger(swrTtlSeconds == null ? SDKConstants.DEFAULT_SWR_TTL_SECONDS : swrTtlSeconds);
        this.backgroundFetchInterval = backgroundFetchInterval;
        this.retryPolicy = retryPolicy == null ? new FeatureFetchRetryPolicy() : retryPolicy;
        this.requestBodyForRemoteEval = requestBodyForRemoteEval;
        this.engine = FeatureRefreshEngine.builder()
                .endpoints(this.endpoints)
                .transport(new UrlConnectionFeatureTransport())
                .cache(new FeatureCacheStore(this.endpoints.cacheKey(), Boolean.TRUE.equals(isCacheDisabled), cacheManager))
                .decryptionKey(encryptionKey)
                .strategy(refreshStrategy)
                .swrTtlSeconds(this.swrTtlSeconds.get())
                .backgroundFetchInterval(backgroundFetchInterval)
                .retryPolicy(this.retryPolicy)
                .swrRefresh(FeatureRefreshEngine.SwrRefresh.ON_READ)
                .remoteEvalBody(requestBodyForRemoteEval)
                .build();
    }

    /**
     * Endpoint for GET request
     */
    public String getFeaturesEndpoint() {
        return endpoints.features();
    }

    /**
     * Endpoint for POST request
     */
    public String getRemoteEvalEndPoint() {
        return endpoints.remoteEval();
    }

    /**
     * Endpoint for SSE request
     */
    public String getEventsEndpoint() {
        return endpoints.events();
    }

    /**
     * Strategy for building url; a server-sent events repository falls back to
     * stale-while-revalidate when the server doesn't support them.
     */
    public FeatureRefreshStrategy getRefreshStrategy() {
        return engine.strategy();
    }

    /**
     * The key used to decrypt encrypted features from the API
     */
    @Nullable
    public String getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * Optional minimum interval between non-forced feature refreshes.
     */
    @Nullable
    public Duration getBackgroundFetchInterval() {
        return backgroundFetchInterval;
    }

    /**
     * Epoch second after which the features are stale
     */
    public AtomicLong getExpiresAt() {
        return engine.expiresAt();
    }

    /**
     * Flag to know whether the repository is initialized
     */
    public AtomicBoolean getInitialized() {
        return engine.initialized();
    }

    /**
     * Request body that be sent with POST request for remote eval feature
     */
    @Nullable
    public RequestBodyForRemoteEval getRequestBodyForRemoteEval() {
        return requestBodyForRemoteEval;
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
     * Method for initialize {@link NativeJavaGbFeatureRepository}. Depends on {@link FeatureRefreshStrategy}
     * connection would be established for SSE or for just GET request
     * @param retryOnFailure:  Boolean argument that responsible whether SSE connection need to be reconnected
     * @throws FeatureFetchException during fetchFeatures function
     */
    @Override
    public void initialize(Boolean retryOnFailure) throws FeatureFetchException {
        engine.initialize(Boolean.TRUE.equals(retryOnFailure));
    }

    /**
     * Get method for saved Group json
     * @return saved Group Json in format of String type
     */
    @Nullable
    public String getSavedGroupsJson() {
        return engine.snapshot().getSavedGroupsJson();
    }

    /**
     * Returns the features JSON, refreshing it first on this thread when it is stale under
     * {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE}.
     *
     * @return Feature Json in format of String
     */
    @Override
    public String getFeaturesJson() {
        engine.refreshIfExpiredOnRead();
        return engine.snapshot().getFeaturesJson();
    }

    /**
     * Returns the parsed features, refreshing them first on this thread when they are stale under
     * {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE}.
     *
     * @return parsed feature definitions
     */
    public Map<String, Feature<?>> getFeaturesMap() {
        engine.refreshIfExpiredOnRead();
        return engine.snapshot().getParsedFeatures();
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
        engine.addCallback(callback);
    }

    @Override
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        engine.addListener(listener);
    }

    @Override
    public void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        engine.removeListener(listener);
    }

    /**
     * Clears the feature refresh callbacks
     *
     * @deprecated Use listener-specific unsubscription with
     * {@link #removeFeatureRefreshListener(FeatureRefreshListener)} where available.
     */
    @Deprecated
    @Override
    public void clearCallbacks() {
        engine.clearCallbacks();
    }

    @Override
    @Nullable
    public Throwable getLastRefreshError() {
        return engine.metrics().lastError();
    }

    @Override
    public long getLastRefreshErrorAtMillis() {
        return engine.metrics().lastErrorAtMillis();
    }

    @Override
    public long getRefreshSuccessCount() {
        return engine.metrics().successCount();
    }

    @Override
    public long getRefreshFailureCount() {
        return engine.metrics().failureCount();
    }

    @Override
    public long getRefreshConsecutiveFailureCount() {
        return engine.metrics().consecutiveFailures();
    }

    @Override
    public long getLastRefreshFailureAtMillis() {
        return engine.metrics().lastFailureAtMillis();
    }

    @Override
    @Nullable
    public Boolean getLastRefreshLoadedFromCache() {
        return engine.metrics().lastLoadedFromCache();
    }

    public void fetchFeatures() throws FeatureFetchException {
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
    }

    public void refreshFeatures() throws FeatureFetchException {
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
    }

    public void refreshFeatures(RefreshMode refreshMode) throws FeatureFetchException {
        engine.refresh(refreshMode, FeatureRefreshSource.MANUAL);
    }

    public void requestFeatureRefresh(RefreshMode refreshMode) {
        engine.requestRefresh(refreshMode);
    }

    /**
     * Calls the registered {@link FeatureRefreshCallback}s as after a successful refresh.
     */
    public void onRefreshSuccess(String featuresJson) {
        engine.notifyCallbacksOfRefresh(featuresJson);
    }

    /**
     * Calls the registered {@link FeatureRefreshCallback}s as after a failed refresh.
     */
    public void onRefreshFailed(Throwable throwable) {
        engine.notifyCallbacksOfError(throwable);
    }

    public void fetchForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        engine.fetchForRemoteEval(requestBodyForRemoteEval, FeatureRefreshSource.REMOTE_EVALUATION);
    }

    /**
     * Stops server-sent events and scheduled refreshes, drops callbacks and listeners, and
     * releases the cache manager. Held features stay readable.
     */
    public void shutdown() {
        engine.shutdown();
    }
}
