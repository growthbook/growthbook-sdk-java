package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.Experiment;
import growthbook.sdk.java.ExperimentResult;
import growthbook.sdk.java.FeatureRefreshStrategy;
import growthbook.sdk.java.FeatureResult;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;

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
                   @Nullable FeatureRefreshStrategy refreshStrategy) {
        this.enabled = enabled == null || enabled;
        this.isQaMode = isQaMode != null && isQaMode;
        this.isCacheDisabled = isCacheDisabled == null || isCacheDisabled;
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
    }

    // ##### Common Options #######
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

    private Boolean isCacheDisabled; // No existing java implementation (NEJI). - default - true!

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

    // debug?: boolean; - No implementation at all? What happens when debug is on? Why Java SDK doesn't implement it?

    /**
     * Optional decryption Key. If this is not null, featuresJson should be an encrypted payload.
     */
    @Nullable
    private String decryptionKey;

    // ##### Common Options #######

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

    @Nullable
    private FeatureRefreshStrategy refreshStrategy;

    public FeatureRefreshStrategy getRefreshingStrategy() {
        if (this.refreshStrategy == null) {
            return FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        }
        return this.refreshStrategy;
    }

    @Nullable
    public StickyBucketService getStickyBucketService() {
        return stickyBucketService;
    }

    public void setInMemoryStickyBucketService() {
        this.setStickyBucketService(new InMemoryStickyBucketServiceImpl(new HashMap<>()));
    }
}
