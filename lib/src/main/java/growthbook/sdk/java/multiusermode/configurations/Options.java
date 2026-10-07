package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonElement;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Data
@Slf4j
public class Options {

    private static final String EMPTY_ATTRIBUTES_JSON = "{}";

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
                null,
                plugins
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
                plugins
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
                   @Nullable List<GrowthBookPlugin> plugins
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
        this.attributesJson.set(globalAttributes == null ? EMPTY_ATTRIBUTES_JSON : globalAttributes.toString());
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
     * Single source of truth for the global user attributes, held as an immutable JSON string
     * inside a thread-safe reference.
     *
     * <p>Storing the canonical string (rather than a shared, mutable {@link JsonObject}) is what
     * makes attribute access thread-safe: {@link #getGlobalAttributes()} parses a fresh, private
     * object on every call, so no caller can ever mutate state observed by another thread, and
     * writers swap the whole string atomically (via {@link AtomicReference#updateAndGet}). A bare
     * {@code volatile JsonObject} would only publish the reference safely while still handing out a
     * shared mutable object.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final AtomicReference<String> attributesJson =
            new AtomicReference<>(EMPTY_ATTRIBUTES_JSON);

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
     * The global attributes as a canonical JSON string (defaults to {@code "{}"} when unset).
     * Kept consistent with {@link #getGlobalAttributes()} on every mutation.
     *
     * @return the JSON string form of the global attributes; never {@code null}
     */
    public String getAttributesJson() {
        return this.attributesJson.get();
    }

    /**
     * Sets the raw global attributes JSON string.
     *
     * @param attributesJson JSON object string of global attributes, or {@code null} to clear
     * @deprecated retained for binary/source compatibility with the Lombok-generated setter this
     * method replaced; it now writes through the thread-safe attribute store. Prefer
     * {@link #setGlobalAttributes(String)} (replace) or {@link #updateGlobalAttributes(String)} (merge).
     */
    @Deprecated
    public void setAttributesJson(@Nullable String attributesJson) {
        setGlobalAttributes(attributesJson);
    }

    /**
     * Returns a fresh, caller-owned copy of the global attributes. A new object is parsed on every
     * call, so callers may read or mutate the result without affecting other threads or the SDK's
     * internal state. Never returns {@code null} (an empty object is returned when unset).
     *
     * @return a private snapshot of the current global attributes
     */
    public JsonObject getGlobalAttributes() {
        return TransformationUtil.transformAttributes(this.attributesJson.get());
    }

    /**
     * Replaces all global attributes (replace semantics).
     *
     * <p>The previous attributes are discarded entirely; only the keys present in
     * {@code attributesJson} remain afterwards. A {@code null} or malformed JSON string clears the
     * attributes (they become an empty object), matching the historical behavior.
     *
     * <p>For additive updates that preserve existing keys, use
     * {@link #updateGlobalAttributes(String)} instead.
     *
     * @param attributesJson JSON object string of global attributes, or {@code null} to clear
     */
    public void setGlobalAttributes(@Nullable String attributesJson) {
        String attributes = TransformationUtil.transformAttributes(attributesJson).toString();
        this.attributesJson.set(attributes);
    }

    /**
     * Shallow-merges the supplied attributes into the current global attributes (merge semantics),
     * mirroring the TypeScript SDK's {@code updateAttributes()}.
     *
     * <p>Merge rules:
     * <ul>
     *   <li>new keys are added;</li>
     *   <li>existing keys are overwritten with the supplied values;</li>
     *   <li>keys not mentioned in {@code attributesJson} are preserved;</li>
     *   <li>a key whose value is JSON {@code null} (e.g. {@code {"plan":null}}) is removed.</li>
     * </ul>
     *
     * <p>A {@code null} or malformed JSON string is a no-op: the existing attributes are left
     * unchanged. Only the top level is merged (shallow); nested objects are replaced wholesale.
     *
     * @param attributesJson JSON object string of attributes to merge in
     * @return {@code true} if a merge was applied, {@code false} if the input was a no-op
     *         ({@code null}, malformed, or empty)
     */
    public boolean updateGlobalAttributes(@Nullable String attributesJson) {
        return updateGlobalAttributes(TransformationUtil.transformAttributes(attributesJson));
    }

    /**
     * {@link JsonObject} overload of {@link #updateGlobalAttributes(String)}; see that method for
     * the full merge semantics (add / overwrite / preserve, and JSON {@code null} removes a key).
     *
     * <p>A {@code null} or empty argument is a no-op.
     *
     * @param attributes attributes to merge into the current global attributes
     * @return {@code true} if a merge was applied, {@code false} if the argument was {@code null} or empty
     */
    public boolean updateGlobalAttributes(@Nullable JsonObject attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return false;
        }
        this.attributesJson.updateAndGet(currentJson -> {
            JsonObject merged = TransformationUtil.transformAttributes(currentJson);
            for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull()) {
                    merged.remove(entry.getKey());
                } else {
                    merged.add(entry.getKey(), value);
                }
            }
            return merged.toString();
        });
        return true;
    }

    public boolean isRemoteEvalEnabled() {
        return Boolean.TRUE.equals(this.remoteEval);
    }
}
