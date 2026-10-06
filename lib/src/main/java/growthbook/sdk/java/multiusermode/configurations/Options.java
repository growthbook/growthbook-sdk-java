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
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.util.ForcedVariationsUtils;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class Options {

    /**
     * Backward-compatible constructor retained for integrations created before
     * background refresh intervals and retry policies were introduced.
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
                   @Nullable Map<String, Integer> globalForcedVariationsMap,
                   @Nullable GbCacheManager cacheManager,
                   @Nullable CacheMode cacheMode,
                   @Nullable String cacheDirectory
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
     * {@link #sseReconnectOnFailure} was introduced.
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
                plugins
        );
    }

    /**
     * Backward-compatible constructor matching the positional signature before custom request
     * headers and a dedicated streaming host ({@link #apiHostRequestHeaders}, {@link #streamingHost},
     * {@link #streamingHostRequestHeaders}) were introduced.
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
                sseReconnectOnFailure,
                plugins,
                null,
                null,
                null
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
                   @Nullable Boolean sseReconnectOnFailure,
                   @Nullable List<GrowthBookPlugin> plugins,
                   @Nullable Map<String, String> apiHostRequestHeaders,
                   @Nullable String streamingHost,
                   @Nullable Map<String, String> streamingHostRequestHeaders
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
        this.sseReconnectOnFailure = sseReconnectOnFailure;
        this.plugins = plugins;
        this.apiHostRequestHeaders = apiHostRequestHeaders;
        this.streamingHost = streamingHost;
        this.streamingHostRequestHeaders = streamingHostRequestHeaders;
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

    /**
     * Custom HTTP headers added to every request against the {@code apiHost}
     * (features fetch and remote evaluation). Useful when GrowthBook is behind a
     * gateway or proxy that requires authentication headers.
     *
     * <p>Values may contain secrets and are never logged by the SDK. The SDK-managed
     * headers {@code User-Agent}, {@code Accept}, {@code If-None-Match} and {@code Cache-Control}
     * are reserved and rejected at startup by {@link OptionsValidator}.
     */
    @Nullable
    @ToString.Exclude
    private Map<String, String> apiHostRequestHeaders;

    /**
     * Optional dedicated host for SSE streaming (e.g. GrowthBook Cloud's
     * {@code https://beacon.growthbook.io}). When unset, streaming connects to
     * {@code apiHost}. Must be a valid {@code http(s)} URL.
     */
    @Nullable
    private String streamingHost;

    /**
     * Custom HTTP headers added to the SSE streaming request. Follows the same
     * rules as {@link #apiHostRequestHeaders}.
     */
    @Nullable
    @ToString.Exclude
    private Map<String, String> streamingHostRequestHeaders;

    /**
     * Optional decryption Key. If this is not null, featuresJson should be an encrypted payload.
     */
    @Nullable
    private String decryptionKey;

    /**
     * List of user's attributes keys.
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

    @Nullable
    private FeatureRefreshCallback featureRefreshCallback;

    @Nullable
    private GbCacheManager cacheManager;

    // New cache configuration
    private CacheMode cacheMode;

    @Nullable
    private String cacheDirectory;

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

    /**
     * Optional minimum interval between non-forced background feature refreshes.
     */
    @Nullable
    private Duration backgroundFetchInterval;

    /**
     * Optional bounded retry policy. Repositories use the default policy when null.
     */
    @Nullable
    private FeatureFetchRetryPolicy retryPolicy;

    /**
     * Whether the multi-user client reconnects the SSE stream after an abnormal failure or a
     * server close. Reconnect attempts are bounded by {@link #retryPolicy}. Defaults to {@code true}.
     */
    @Nullable
    private Boolean sseReconnectOnFailure;

    public boolean isSseReconnectOnFailure() {
        return sseReconnectOnFailure == null || sseReconnectOnFailure;
    }

    @Nullable
    public String getCacheDirectory() {
        return cacheDirectory;
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

    public void setGlobalAttributes(@Nullable String attributesJson) {
        this.attributesJson = attributesJson;
        this.globalAttributes = TransformationUtil.transformAttributes(attributesJson);
    }

    public boolean isRemoteEvalEnabled() {
        return Boolean.TRUE.equals(this.remoteEval);
    }
}
