package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.util.ExperimentHelper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class Options {

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
                   @Nullable FeatureRefreshCallback featureRefreshCallback,
                   @Nullable JsonObject globalAttributes,
                   @Nullable Map<String, Object> globalForcedFeatureValues,
                   @Nullable Map<String, Integer> globalForcedVariationsMap

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
        this.featureRefreshCallback = featureRefreshCallback;
        this.globalAttributes = globalAttributes;
        this.globalForcedFeatureValues = globalForcedFeatureValues;
        this.globalForcedVariationsMap = globalForcedVariationsMap;
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

    public FeatureRefreshStrategy getRefreshingStrategy() {
        if (this.refreshStrategy == null) {
            return FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        }
        return this.refreshStrategy;
    }

    @Nullable
    private FeatureRefreshCallback featureRefreshCallback;

    @Nullable
    public StickyBucketService getStickyBucketService() {
        return stickyBucketService;
    }

    public void setInMemoryStickyBucketService() {
        this.setStickyBucketService(new InMemoryStickyBucketServiceImpl(new HashMap<>()));
    }

    public void setGlobalAttributes(@Nullable String attributesJson) {
        this.attributesJson = attributesJson;
        this.globalAttributes = TransformationUtil.transformAttributes(attributesJson);
    }
}
