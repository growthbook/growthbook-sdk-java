package growthbook.sdk.java.repository;

import com.google.gson.JsonObject;
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
import growthbook.sdk.java.repository.internal.OkHttpFeatureTransport;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import lombok.Builder;
import lombok.Getter;
import okhttp3.OkHttpClient;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided.
 * Initialize with {@link GBFeaturesRepository#initialize()}
 * Get the features JSON with {@link GBFeaturesRepository#getFeaturesJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 *
 * <p>Fetches over OkHttp and, under {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE},
 * refreshes on a background polling thread. Shared across threads; the refresh logic lives in
 * {@link FeatureRefreshEngine}.
 */
public class GBFeaturesRepository implements IGBFeaturesRepository {

    public static final String FILE_PATH_FOR_CACHE = "src/main/resources";

    public static final String EMPTY_JSON_OBJECT_STRING = "{}";

    private final FeatureEndpoints endpoints;

    private final FeatureRefreshEngine engine;

    @Nullable
    private final String decryptionKey;

    /**
     * -- GETTER --
     *  The standard cache TTL to use (60 seconds)
     */
    @Getter
    private final Integer swrTtlSeconds;

    @Nullable
    private final Duration backgroundFetchInterval;

    /**
     * -- GETTER --
     *  Bounded retry policy for feature fetch requests.
     */
    @Getter
    private final FeatureFetchRetryPolicy retryPolicy;

    /**
     * -- GETTER --
     *  Request body for that be sent with POST request for remote eval feature
     */
    @Getter
    private final RequestBodyForRemoteEval requestBodyForRemoteEval;

    /**
     * Create a new GBFeaturesRepository
     *
     * @param apiHost       The GrowthBook API host (default: <a href="https://cdn.growthbook.io">...</a>)
     * @param clientKey     Your client ID, e.g. sdk-abc123
     * @param encryptionKey optional key for decrypting encrypted payload
     * @param swrTtlSeconds How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     */
    @Deprecated
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Nullable String encryptionKey,
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
            @Nullable String encryptionKey,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            @Nullable Integer swrTtlSeconds,
            @Nullable RequestBodyForRemoteEval requestBodyForRemoteEval
    ) {
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, null, null, requestBodyForRemoteEval, null);
    }

    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Nullable String encryptionKey,
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
            @Nullable String encryptionKey,
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

    @Builder
    public GBFeaturesRepository(
            @Nullable String apiHost,
            String clientKey,
            @Nullable String encryptionKey,
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
        this(apiHost, clientKey, (decryptionKey != null) ? decryptionKey : encryptionKey,
                refreshStrategy,
                swrTtlSeconds,
                okHttpClient,
                isCacheDisabled,
                (requestBodyForRemoteEval != null) ? requestBodyForRemoteEval : new RequestBodyForRemoteEval(),
                cacheManager,
                backgroundFetchInterval,
                retryPolicy
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
        if (backgroundFetchInterval != null && backgroundFetchInterval.isNegative()) {
            throw new IllegalArgumentException("backgroundFetchInterval must not be negative");
        }
        this.endpoints = FeatureEndpoints.of(apiHost, clientKey);
        this.decryptionKey = decryptionKey;
        this.swrTtlSeconds = swrTtlSeconds == null ? SDKConstants.DEFAULT_SWR_TTL_SECONDS : swrTtlSeconds;
        this.backgroundFetchInterval = backgroundFetchInterval;
        this.retryPolicy = retryPolicy == null ? new FeatureFetchRetryPolicy() : retryPolicy;
        this.requestBodyForRemoteEval = requestBodyForRemoteEval != null ? requestBodyForRemoteEval : new RequestBodyForRemoteEval();
        this.engine = FeatureRefreshEngine.builder()
                .endpoints(this.endpoints)
                .transport(new OkHttpFeatureTransport(okHttpClient))
                .cache(new FeatureCacheStore(this.endpoints.cacheKey(), Boolean.TRUE.equals(isCacheDisabled), cacheManager))
                .decryptionKey(decryptionKey)
                .strategy(refreshStrategy)
                .swrTtlSeconds(this.swrTtlSeconds)
                .backgroundFetchInterval(backgroundFetchInterval)
                .retryPolicy(this.retryPolicy)
                .swrRefresh(FeatureRefreshEngine.SwrRefresh.POLLING)
                .remoteEvalBody(this.requestBodyForRemoteEval)
                .build();
    }

    /**
     * @return the engine that refreshes this repository's features
     */
    FeatureRefreshEngine engine() {
        return engine;
    }

    /**
     * Endpoint for GET request
     */
    public String getFeaturesEndpoint() {
        return endpoints.features();
    }

    /**
     * Endpoint for SSE request
     */
    public String getEventsEndpoint() {
        return endpoints.events();
    }

    /**
     * Endpoint for POST request
     */
    public String getRemoteEvalEndPoint() {
        return endpoints.remoteEval();
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
    public String getDecryptionKey() {
        return decryptionKey;
    }

    /**
     * @deprecated Use {@link #getDecryptionKey()} instead.
     */
    @Deprecated
    @Nullable
    public String getEncryptionKey() {
        return decryptionKey;
    }

    /**
     * Optional minimum interval between non-forced feature refreshes.
     */
    @Nullable
    public Duration getBackgroundFetchInterval() {
        return backgroundFetchInterval;
    }

    /**
     * @return the epoch second at which the cached payload expires, or {@code null} before it
     * has been set
     */
    @Nullable
    public Long getExpiresAt() {
        long value = engine.expiresAt().get();
        return value == 0 ? null : value;
    }

    /**
     * The current features payload as one immutable snapshot. Prefer this over the
     * individual getters when consuming more than one part of the payload.
     *
     * @return the snapshot from the most recent successful refresh
     */
    public FeatureSnapshot getFeatureSnapshot() {
        return engine.snapshot();
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
        return engine.snapshot().getSavedGroupsJson();
    }

    /**
     * Allows you to get the features JSON from the provided {@link GBFeaturesRepository#getFeaturesEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your features would not have loaded.
     *
     * @return feature data JSON in a type of String. Handle refresh strategy
     */
    public String getFeaturesJson() {
        return engine.snapshot().getFeaturesJson();
    }

    /**
     * Keys are unique identifiers for the features and the values are Feature objects.
     * Feature definitions - To be pulled from API / Cache
     *
     * @return parsed feature definitions
     */
    public Map<String, Feature<?>> getParsedFeatures() {
        return engine.snapshot().getParsedFeatures();
    }

    public JsonObject getParsedSavedGroups() {
        return engine.snapshot().getParsedSavedGroups();
    }

    public void setCacheManager(GbCacheManager cacheManager) {
        engine.cache().setManager(cacheManager);
    }

    /**
     * @return the cache manager, or {@code null} when caching is disabled or after shutdown
     */
    @Nullable
    public GbCacheManager getCacheManager() {
        return engine.cache().manager();
    }

    public boolean isCacheDisabled() {
        return engine.cache().isDisabled();
    }

    @Nullable
    public Long getCacheLastUpdatedMillis() {
        return engine.cache().lastUpdatedMillis();
    }

    public long getLastSuccessfulFetchAtMillis() {
        return engine.metrics().lastSuccessfulFetchAtMillis();
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

    public boolean hasFeatureData() {
        return engine.hasFeatureData();
    }

    public boolean isSseAllowed() {
        return engine.isSseAllowed();
    }

    public boolean isSseConnected() {
        return engine.isSseConnected();
    }

    public int getSseRetryAttempts() {
        return engine.sseRetryAttempts();
    }

    public int getActiveFeatureCount() {
        return engine.activeFeatureCount();
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
    @Override
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        engine.addListener(listener);
    }

    @Override
    public void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        engine.removeListener(listener);
    }

    /**
     * @deprecated Use listener-specific unsubscription with
     * {@link #removeFeatureRefreshListener(FeatureRefreshListener)} where available.
     */
    @Deprecated
    @Override
    public void clearCallbacks() {
        engine.clearCallbacks();
    }

    @Override
    public void initialize() throws FeatureFetchException {
        initialize(false);
    }

    @Override
    public void initialize(Boolean retryOnFailure) throws FeatureFetchException {
        engine.initialize(Boolean.TRUE.equals(retryOnFailure));
    }

    /**
     * @return whether the repository has been initialized
     */
    public Boolean getInitialized() {
        return engine.initialized().get();
    }

    /**
     * Performs a network request to fetch the features from the GrowthBook API
     * with the provided endpoint.
     * If an encryptionKey is provided, it is assumed the features endpoint is using encrypted features.
     * This method will attempt to decrypt the encrypted features with the provided encryptionKey.
     */
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

    public void fetchForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        engine.fetchForRemoteEval(requestBodyForRemoteEval, FeatureRefreshSource.REMOTE_EVALUATION);
    }

    public void shutdown() {
        engine.shutdown();
    }
}
