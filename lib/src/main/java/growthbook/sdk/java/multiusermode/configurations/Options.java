package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import growthbook.sdk.java.plugin.PluginRegistry;
import growthbook.sdk.java.remoteeval.RemoteEvalRequestBuilder;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.util.ForcedVariationsUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Data
@Slf4j
public class Options {

    /** Default for {@link #stickyBucketTimeout}. */
    public static final Duration DEFAULT_STICKY_BUCKET_TIMEOUT = Duration.ofSeconds(5);

    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Deprecated @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, Integer> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory) {
        this(
                enabled,
                isQaMode,
                isCacheDisabled,
                allowUrlOverrides,
                url,
                apiHost,
                clientKey,
                decryptionKey,
                stickyBucketIdentifierAttributes,
                stickyBucketService,
                trackingCallBackWithUser,
                featureUsageCallbackWithUser,
                refreshStrategy,
                swrTtlSeconds,
                featureRefreshCallback,
                globalAttributes,
                globalForcedFeatureValues,
                globalForcedVariationsMap,
                cacheManager,
                cacheMode,
                cacheDirectory,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Backward-compatible constructor matching the 0.11.0 positional signature, before
     * {@link #featureRefreshListenerExecutor} and {@link #sseReconnectOnFailure} were introduced.
     */
    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, ?> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory,
                   @Nullable Boolean remoteEval,
                   @Nullable List<String> cacheKeyAttributes,
                   @Nullable Integer remoteEvalCacheSize,
                   @Nullable Integer remoteEvalCacheTtlSeconds,
                   @Nullable Duration backgroundFetchInterval,
                   @Nullable FeatureFetchRetryPolicy retryPolicy,
                   @Nullable List<GrowthBookPlugin> plugins,
                   @Nullable AsyncStickyBucketService asyncStickyBucketService,
                   @Nullable Executor asyncExecutor,
                   @Nullable Long stickyBucketCacheTtlSeconds,
                   @Nullable Integer stickyBucketCacheSize
    ) {
        this(
                enabled,
                isQaMode,
                isCacheDisabled,
                allowUrlOverrides,
                url,
                apiHost,
                clientKey,
                decryptionKey,
                stickyBucketIdentifierAttributes,
                stickyBucketService,
                trackingCallBackWithUser,
                featureUsageCallbackWithUser,
                refreshStrategy,
                swrTtlSeconds,
                featureRefreshCallback,
                globalAttributes,
                globalForcedFeatureValues,
                globalForcedVariationsMap,
                cacheManager,
                cacheMode,
                cacheDirectory,
                remoteEval,
                cacheKeyAttributes,
                remoteEvalCacheSize,
                remoteEvalCacheTtlSeconds,
                backgroundFetchInterval,
                retryPolicy,
                null,
                null,
                plugins,
                asyncStickyBucketService,
                asyncExecutor,
                stickyBucketCacheTtlSeconds,
                stickyBucketCacheSize,
                null,
                null
        );
    }

    /**
     * Backward-compatible constructor matching the positional signature on {@code main} before
     * {@link #featureRefreshListenerExecutor} was introduced.
     *
     * <p>That signature was added after the 0.11.0 release and never shipped, so this overload
     * exists only so the branch does not narrow {@code main}'s surface for callers built against
     * an unreleased snapshot. The released signatures are the 23- and 30-argument ones above.
     */
    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Deprecated @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, ?> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory,
                   @Nullable Boolean remoteEval,
                   @Nullable List<String> cacheKeyAttributes,
                   @Nullable Integer remoteEvalCacheSize,
                   @Nullable Integer remoteEvalCacheTtlSeconds,
                   @Nullable Duration backgroundFetchInterval,
                   @Nullable FeatureFetchRetryPolicy retryPolicy,
                   @Nullable Boolean sseReconnectOnFailure,
                   @Nullable List<GrowthBookPlugin> plugins
    ) {
        this(
                enabled,
                isQaMode,
                isCacheDisabled,
                allowUrlOverrides,
                url,
                apiHost,
                clientKey,
                decryptionKey,
                stickyBucketIdentifierAttributes,
                stickyBucketService,
                trackingCallBackWithUser,
                featureUsageCallbackWithUser,
                refreshStrategy,
                swrTtlSeconds,
                featureRefreshCallback,
                globalAttributes,
                globalForcedFeatureValues,
                globalForcedVariationsMap,
                cacheManager,
                cacheMode,
                cacheDirectory,
                remoteEval,
                cacheKeyAttributes,
                remoteEvalCacheSize,
                remoteEvalCacheTtlSeconds,
                backgroundFetchInterval,
                retryPolicy,
                null,
                sseReconnectOnFailure,
                plugins,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Backward-compatible constructor matching {@code main}'s 0.11.x positional signature, before
     * {@link #featureRefreshListenerExecutor} and {@link #sseReconnectOnFailure} were introduced.
     */
    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, ?> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory,
                   @Nullable Boolean remoteEval,
                   @Nullable List<String> cacheKeyAttributes,
                   @Nullable Integer remoteEvalCacheSize,
                   @Nullable Integer remoteEvalCacheTtlSeconds,
                   @Nullable Duration backgroundFetchInterval,
                   @Nullable FeatureFetchRetryPolicy retryPolicy,
                   @Nullable List<GrowthBookPlugin> plugins
    ) {
        this(
                enabled, isQaMode, isCacheDisabled, allowUrlOverrides, url, apiHost, clientKey,
                decryptionKey, stickyBucketIdentifierAttributes, stickyBucketService,
                trackingCallBackWithUser, featureUsageCallbackWithUser, refreshStrategy, swrTtlSeconds,
                featureRefreshCallback, globalAttributes, globalForcedFeatureValues,
                globalForcedVariationsMap, cacheManager, cacheMode, cacheDirectory, remoteEval,
                cacheKeyAttributes, remoteEvalCacheSize, remoteEvalCacheTtlSeconds,
                backgroundFetchInterval, retryPolicy, null, null, plugins, null, null, null, null, null, null
        );
    }

    /**
     * Full positional signature on {@code main} before the async sticky-bucket options were
     * introduced. Kept as a delegating overload so the published 30-argument arity is preserved.
     */
    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, ?> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory,
                   @Nullable Boolean remoteEval,
                   @Nullable List<String> cacheKeyAttributes,
                   @Nullable Integer remoteEvalCacheSize,
                   @Nullable Integer remoteEvalCacheTtlSeconds,
                   @Nullable Duration backgroundFetchInterval,
                   @Nullable FeatureFetchRetryPolicy retryPolicy,
                   @Nullable Executor featureRefreshListenerExecutor,
                   @Nullable Boolean sseReconnectOnFailure,
                   @Nullable List<GrowthBookPlugin> plugins
    ) {
        this(
                enabled, isQaMode, isCacheDisabled, allowUrlOverrides, url, apiHost, clientKey,
                decryptionKey, stickyBucketIdentifierAttributes, stickyBucketService,
                trackingCallBackWithUser, featureUsageCallbackWithUser, refreshStrategy, swrTtlSeconds,
                featureRefreshCallback, globalAttributes, globalForcedFeatureValues,
                globalForcedVariationsMap, cacheManager, cacheMode, cacheDirectory, remoteEval,
                cacheKeyAttributes, remoteEvalCacheSize, remoteEvalCacheTtlSeconds,
                backgroundFetchInterval, retryPolicy, featureRefreshListenerExecutor,
                sseReconnectOnFailure, plugins, null, null, null, null, null, null
        );
    }

    /**
     * Backward-compatible constructor preserving the positional signature before {@code requestTimeout}
     * was added. Delegates with no request timeout, so HTTP behaviour is unchanged.
     */
    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Deprecated @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, ?> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory,
                   @Nullable Boolean remoteEval,
                   @Nullable List<String> cacheKeyAttributes,
                   @Nullable Integer remoteEvalCacheSize,
                   @Nullable Integer remoteEvalCacheTtlSeconds,
                   @Nullable Duration backgroundFetchInterval,
                   @Nullable FeatureFetchRetryPolicy retryPolicy,
                   @Nullable Executor featureRefreshListenerExecutor,
                   @Nullable Boolean sseReconnectOnFailure,
                   @Nullable List<GrowthBookPlugin> plugins,
                   @Nullable AsyncStickyBucketService asyncStickyBucketService,
                   @Nullable Executor asyncExecutor,
                   @Nullable Long stickyBucketCacheTtlSeconds,
                   @Nullable Integer stickyBucketCacheSize,
                   @Nullable Duration stickyBucketTimeout
    ) {
        this(
                enabled, isQaMode, isCacheDisabled, allowUrlOverrides, url, apiHost, clientKey,
                decryptionKey, stickyBucketIdentifierAttributes, stickyBucketService,
                trackingCallBackWithUser, featureUsageCallbackWithUser, refreshStrategy, swrTtlSeconds,
                featureRefreshCallback, globalAttributes, globalForcedFeatureValues,
                globalForcedVariationsMap, cacheManager, cacheMode, cacheDirectory, remoteEval,
                cacheKeyAttributes, remoteEvalCacheSize, remoteEvalCacheTtlSeconds,
                backgroundFetchInterval, retryPolicy, featureRefreshListenerExecutor,
                sseReconnectOnFailure, plugins, asyncStickyBucketService, asyncExecutor,
                stickyBucketCacheTtlSeconds, stickyBucketCacheSize, stickyBucketTimeout, null
        );
    }

    @Builder
    public Options(@Nullable Boolean enabled,
                   Boolean isQaMode,
                   @Nullable Boolean isCacheDisabled,
                   Boolean allowUrlOverrides,
                   @Nullable String url,
                   @Nullable String apiHost,
                   @Nullable String clientKey,
                   @Nullable String decryptionKey,
                   @Nullable List<String> stickyBucketIdentifierAttributes,
                   @Nullable StickyBucketService stickyBucketService,
                   @Nullable TrackingCallbackWithUser trackingCallBackWithUser,
                   @Nullable FeatureUsageCallbackWithUser featureUsageCallbackWithUser,
                   @Nullable FeatureRefreshStrategy refreshStrategy,
                   @Nullable Integer swrTtlSeconds,
                   @Deprecated @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, ?> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory,
                   @Nullable Boolean remoteEval,
                   @Nullable List<String> cacheKeyAttributes,
                   @Nullable Integer remoteEvalCacheSize,
                   @Nullable Integer remoteEvalCacheTtlSeconds,
                   @Nullable Duration backgroundFetchInterval,
                   @Nullable FeatureFetchRetryPolicy retryPolicy,
                   @Nullable Executor featureRefreshListenerExecutor,
                   @Nullable Boolean sseReconnectOnFailure,
                   @Nullable List<GrowthBookPlugin> plugins,
                   @Nullable AsyncStickyBucketService asyncStickyBucketService,
                   @Nullable Executor asyncExecutor,
                   @Nullable Long stickyBucketCacheTtlSeconds,
                   @Nullable Integer stickyBucketCacheSize,
                   @Nullable Duration stickyBucketTimeout,
                   @Nullable Duration requestTimeout
    ) {
        this.enabled = enabled == null || enabled;
        this.isQaMode = isQaMode != null && isQaMode;
        this.isCacheDisabled = isCacheDisabled != null && isCacheDisabled;
        this.allowUrlOverrides = allowUrlOverrides != null && allowUrlOverrides;
        this.url = url;
        this.apiHost = apiHost;
        this.clientKey = clientKey;
        this.decryptionKey = decryptionKey;
        this.stickyBucketIdentifierAttributes = stickyBucketIdentifierAttributes;
        this.stickyBucketService = stickyBucketService;
        this.trackingCallBackWithUser = trackingCallBackWithUser;
        this.featureUsageCallbackWithUser = featureUsageCallbackWithUser;
        this.refreshStrategy = refreshStrategy;
        this.swrTtlSeconds = swrTtlSeconds;
        this.featureRefreshCallback = featureRefreshCallback;
        this.globalAttributes = globalAttributes;
        this.globalForcedFeatureValues = globalForcedFeatureValues;
        this.globalForcedVariationsMap = ForcedVariationsUtils.normalize(globalForcedVariationsMap);
        this.cacheManager = cacheManager;
        this.cacheMode = cacheMode == null ? CacheMode.AUTO : cacheMode;
        this.cacheDirectory = cacheDirectory;
        this.remoteEval = remoteEval != null && remoteEval;
        this.cacheKeyAttributes = cacheKeyAttributes;
        this.remoteEvalCacheSize = RemoteEvalRequestBuilder.normalizeCacheSize(remoteEvalCacheSize);
        this.remoteEvalCacheTtlSeconds = remoteEvalCacheTtlSeconds;
        this.backgroundFetchInterval = backgroundFetchInterval;
        this.retryPolicy = retryPolicy;
        this.featureRefreshListenerExecutor = featureRefreshListenerExecutor;
        this.sseReconnectOnFailure = sseReconnectOnFailure;
        this.plugins = plugins;
        this.asyncStickyBucketService = asyncStickyBucketService;
        this.asyncExecutor = asyncExecutor;
        this.stickyBucketCacheTtlSeconds = stickyBucketCacheTtlSeconds;
        this.stickyBucketCacheSize = stickyBucketCacheSize == null ? 1000 : stickyBucketCacheSize;
        this.stickyBucketTimeout = stickyBucketTimeout;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Whether globally all experiments are enabled (default: true)
     * Switch to globally disable all experiments.
     */
    @Nullable
    private Boolean enabled;

    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    private Boolean isQaMode;

    // Default - true. NEIJ
    private Boolean isCacheDisabled;

    /**
     * Boolean flag to allow URL overrides (default: false)
     */
    private Boolean allowUrlOverrides;

    @Nullable
    private String url;

    @Nullable
    private String apiHost;

    @Nullable
    private String clientKey;

    /*streamingHost?: string;
    apiHostRequestHeaders?: Record<string, string>;
    streamingHostRequestHeaders?: Record<string, string>;*/

    // Why do you need attributes here?
    //attributes?: Attributes;

    // debug?: boolean; // NEIJ

    /**
     * Optional decryption Key. If this is not null, featuresJson should be an encrypted payload.
     */
    @Nullable
    private String decryptionKey;

    /**
     * Attribute keys whose sticky bucket documents are read for every user, in addition to the
     * attributes the loaded features' experiments hash on (those are always read). Needed only for
     * attributes the SDK cannot derive, such as the hash attribute of an inline experiment.
     */
    @Nullable
    private List<String> stickyBucketIdentifierAttributes;

    /**
     * Service that provide functionality of Sticky Bucketing
     */
    @Nullable
    private StickyBucketService stickyBucketService;

    /**
     * A function that takes {@link Experiment} and {@link ExperimentResult} as arguments.
     */
    @Nullable
    private TrackingCallbackWithUser trackingCallBackWithUser;

    /**
     * A function that takes {@link String} and {@link FeatureResult} as arguments.
     * A callback that will be invoked every time a feature is viewed. Listen for feature usage events
     */
    @Nullable
    private FeatureUsageCallbackWithUser featureUsageCallbackWithUser;

    /**
     * Strategy for building url
     */
    @Nullable
    private FeatureRefreshStrategy refreshStrategy;

    /**
     * The standard cache TTL to use.
     */
    @Nullable
    private Integer swrTtlSeconds;

    /**
     * Map of user attributes that are used to assign variations
     */
    @Nullable
    private JsonObject globalAttributes;

    /**
     * String format of user attributes that are used to assign variations
     */
    @Nullable
    private String attributesJson;

    /**
     * Manual force feature values
     */
    @Nullable
    private Map<String, Object> globalForcedFeatureValues;

    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    @Nullable
    private Map<String, Integer> globalForcedVariationsMap;

    public void setGlobalForcedVariationsMap(@Nullable Map<String, ?> globalForcedVariationsMap) {
        this.globalForcedVariationsMap = ForcedVariationsUtils.normalize(globalForcedVariationsMap);
    }

    public FeatureRefreshStrategy getRefreshingStrategy() {
        if (this.refreshStrategy == null) {
            return FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        }
        return this.refreshStrategy;
    }

    /**
     * Legacy feature refresh callback.
     *
     * @deprecated Use {@code GrowthBookClient.addFeatureRefreshListener(...)} or
     * {@code GrowthBookClient.subscribeFeatureRefreshListener(...)} after constructing the client.
     */
    @Deprecated
    @Nullable
    private FeatureRefreshCallback featureRefreshCallback;

    @Nullable
    private GbCacheManager cacheManager;

    private CacheMode cacheMode;

    @Nullable
    private String cacheDirectory;

    /**
     * Optional executor for client-level feature refresh listeners. When not supplied, the client
     * dispatches listener callbacks on a dedicated daemon thread it owns and shuts down.
     */
    @Nullable
    private Executor featureRefreshListenerExecutor;

    /**
     * Plugins registered with the GrowthBook client. See
     * {@link GrowthBookPlugin} and
     * {@link growthbook.sdk.java.plugin.tracking.GrowthBookTrackingPlugin}.
     * The owning client builds a per-instance {@link PluginRegistry} from this
     * list; the registry itself is carried on {@code EvaluationContext}, not
     * here, so reusing one {@code Options} across clients stays isolated.
     */
    @Nullable
    private List<GrowthBookPlugin> plugins;

    /**
     * Optional minimum interval between non-forced background feature refreshes.
     */
    @Nullable
    private Duration backgroundFetchInterval;

    /**
     * Optional timeout for the features fetch and remote-eval HTTP calls, applied to the whole call
     * ({@code callTimeout}) and to connect, read and write, so each call has a predictable upper bound.
     * Opt-in: when {@code null} (the default) the SDK keeps OkHttp's defaults, as before this option
     * existed, and a caller-supplied {@code OkHttpClient} keeps its own timeouts. {@link Duration#ZERO}
     * disables every timeout. Otherwise it must be between 1 ms and {@link Integer#MAX_VALUE} ms
     * (checked by {@link OptionsValidator}). Does not bound the long-lived SSE stream.
     *
     * <p>This bounds a single request, not a whole initialization: with a retry policy of
     * {@code n} attempts, a persistently unreachable endpoint can take up to {@code n ×
     * requestTimeout} plus the policy's retry backoff between attempts before initialization
     * gives up.
     */
    @Nullable
    private Duration requestTimeout;

    /**
     * Optional bounded retry policy. Repositories use the default policy when null.
     */
    @Nullable
    private FeatureFetchRetryPolicy retryPolicy;

    private Boolean remoteEval;

    @Nullable
    private List<String> cacheKeyAttributes;

    private Integer remoteEvalCacheSize;

    /**
     * Hard expiry (seconds) for cached remote-eval responses; {@code null} disables time-based expiry.
     */
    @Nullable
    private Integer remoteEvalCacheTtlSeconds;

    public CacheMode getCacheMode() { return cacheMode == null ? CacheMode.AUTO : cacheMode; }
    public String getCacheDirectory() { return cacheDirectory; }

    /**
     * Whether the multi-user client reconnects the SSE stream after an abnormal failure or a
     * server close. Reconnect attempts are bounded by {@link #retryPolicy}. Defaults to {@code true}.
     */
    @Nullable
    private Boolean sseReconnectOnFailure;

    /**
     * Non-blocking sticky bucket service for the multi-user client. Mutually
     * exclusive with {@link #stickyBucketService} — configure exactly one.
     * See {@link AsyncStickyBucketService} for the contract.
     *
     * <p>A failed read from either kind of service is logged and that evaluation
     * continues with normal bucketing, but none of its assignments are saved: a
     * variation bucketed without the stored assignment must not replace it.
     */
    @Nullable
    private AsyncStickyBucketService asyncStickyBucketService;

    /**
     * Executor for the client's asynchronous entry points: offloading blocking
     * {@link StickyBucketService} calls and composing async evaluations. The
     * synchronous evaluation API never uses it; it calls a blocking service on
     * the calling thread. When null, the client creates its own bounded daemon
     * pool and shuts it down in {@code shutdown()}; a caller-supplied executor
     * is never shut down by the SDK. On JDK 21+, pass
     * {@code Executors.newVirtualThreadPerTaskExecutor()} so blocking offloads
     * scale with the store instead of a fixed pool.
     */
    @Nullable
    private Executor asyncExecutor;

    /**
     * Opt-in TTL, in seconds, for caching sticky bucket reads per user
     * attributes. Null or non-positive (the default) disables caching:
     * assignments are fetched per evaluation, so assignment changes made by
     * other processes are picked up promptly. Enabling trades that freshness
     * for fewer store lookups on hot users.
     */
    @Nullable
    private Long stickyBucketCacheTtlSeconds;

    /**
     * Maximum entries in the opt-in sticky bucket read cache (default 1000).
     * Non-positive disables caching. Only meaningful when
     * {@link #stickyBucketCacheTtlSeconds} is set.
     */
    private Integer stickyBucketCacheSize;

    /**
     * How long the multi-user client waits for one sticky bucket store call, read or save
     * (default {@link #DEFAULT_STICKY_BUCKET_TIMEOUT}, 5 seconds). A read that takes longer is handled
     * like any failed read: that evaluation continues with normal bucketing and saves
     * nothing. A save that takes longer is handled like any failed save. {@link Duration#ZERO}
     * disables the timeout.
     *
     * <p>The store call itself is not cancelled. A blocking {@link StickyBucketService} called
     * by the synchronous evaluation API runs on the calling thread and is bounded by its own
     * client's timeout, not by this one.
     */
    @Nullable
    private Duration stickyBucketTimeout;

    public Duration getStickyBucketTimeout() {
        return stickyBucketTimeout == null ? DEFAULT_STICKY_BUCKET_TIMEOUT : stickyBucketTimeout;
    }

    public boolean isSseReconnectOnFailure() {
        return sseReconnectOnFailure == null || sseReconnectOnFailure;
    }

    @Nullable
    public StickyBucketService getStickyBucketService() {
        return stickyBucketService;
    }

    public void setInMemoryStickyBucketService() {
        // Thread-safe backing map: this Options instance configures the multi-user
        // GrowthBookClient, which evaluates (and therefore saves assignments) concurrently.
        this.setStickyBucketService(new InMemoryStickyBucketServiceImpl());
    }

    /**
     * Whether either flavor of sticky bucket service is configured.
     *
     * @return true when a sync or async sticky bucket service is set
     */
    public boolean isStickyBucketingConfigured() {
        return this.stickyBucketService != null || this.asyncStickyBucketService != null;
    }

    public void setGlobalAttributes(@Nullable String attributesJson) {
        this.attributesJson = attributesJson;
        this.globalAttributes = TransformationUtil.transformAttributes(attributesJson);
    }

    public boolean isRemoteEvalEnabled() {
        return Boolean.TRUE.equals(this.remoteEval);
    }
}
