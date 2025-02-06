package growthbook.sdk.java.model;

import com.google.gson.JsonObject;
import growthbook.sdk.java.util.ExperimentHelper;
import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object passed into the GrowthBook constructor.
 * The {@link GBContext#builder()} is recommended for constructing a Context.
 * Alternatively, you can use the class's constructor.
 */
@Data
@Slf4j
public class GBContext {

    /**
     * The {@link GBContextBuilder} is recommended for constructing a Context.
     * Alternatively, you can use this static method instead of the builder.
     *
     * @param attributesJson                   User attributes as JSON string
     * @param attributes                       User attributes as JSON Object, either set this or `attributesJson`
     * @param featuresJson                     Features response as JSON string, or the encrypted payload. Encrypted payload requires `encryptionKey`
     * @param features                         Features response as JSON Object, either set this or `featuresJson`
     * @param encryptionKey                    Optional encryption key. If this is not null, featuresJson should be an encrypted payload.
     * @param enabled                          Whether globally all experiments are enabled (default: true)
     * @param isQaMode                         If true, random assignment is disabled and only explicitly forced variations are used.
     * @param url                              A URL string that is used for experiment evaluation, as well as forcing feature values.
     * @param allowUrlOverrides                Boolean flag to allow URL overrides (default: false)
     * @param forcedVariationsMap              Force specific experiments to always assign a specific variation (used for QA)
     * @param trackingCallback                 A function that takes {@link Experiment} and {@link ExperimentResult} as arguments.
     * @param featureUsageCallback             A function that takes {@link String} and {@link FeatureResult} as arguments.
     *                                         A callback that will be invoked every time a feature is viewed. Listen for feature usage events
     * @param stickyBucketService              Service that provide functionality of Sticky Bucketing.
     * @param stickyBucketAssignmentDocs       Map of Sticky Bucket documents.
     * @param stickyBucketIdentifierAttributes List of user's attributes keys.
     */
    @Builder
    public GBContext(
            @Nullable String attributesJson,
            @Nullable JsonObject attributes,
            @Nullable String featuresJson,
            @Nullable JsonObject features,
            @Nullable String encryptionKey,
            @Nullable Boolean enabled,
            Boolean isQaMode,
            @Nullable String url,
            Boolean allowUrlOverrides,
            @Nullable Map<String, Integer> forcedVariationsMap,
            @Nullable TrackingCallback trackingCallback,
            @Nullable FeatureUsageCallback featureUsageCallback,
            @Nullable StickyBucketService stickyBucketService,
            @Nullable Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs,
            @Nullable List<String> stickyBucketIdentifierAttributes,
            @Nullable JsonObject savedGroups
    ) {
        this.encryptionKey = encryptionKey;
        this.attributesJson = attributesJson == null ? "{}" : attributesJson;
        this.attributes = attributes == null ? new JsonObject() : attributes;

        if (featuresJson != null) {
            this.features = TransformationUtil.transformEncryptedFeatures(featuresJson, encryptionKey);
        }
        if (features != null) {
            this.features = features;
        }

        this.enabled = enabled == null || enabled;
        this.isQaMode = isQaMode != null && isQaMode;
        this.allowUrlOverride = allowUrlOverrides != null && allowUrlOverrides;
        this.url = url;
        this.forcedVariationsMap = forcedVariationsMap == null ? new HashMap<>() : forcedVariationsMap;
        this.trackingCallback = trackingCallback;
        this.featureUsageCallback = featureUsageCallback;
        this.stickyBucketService = stickyBucketService;
        this.stickyBucketAssignmentDocs = stickyBucketAssignmentDocs;
        this.stickyBucketIdentifierAttributes = stickyBucketIdentifierAttributes;
        this.savedGroups = savedGroups;
    }

    /**
     * Keys are unique identifiers for the features and the values are Feature objects.
     * Feature definitions - To be pulled from API / Cache
     */
    @Nullable
    private JsonObject features;

    /**
     * Switch to globally disable all experiments. Default true.
     */
    @Nullable
    private Boolean enabled;

    /**
     * The URL of the current page
     */
    @Nullable
    private String url;

    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    private Boolean isQaMode;

    /**
     * Boolean flag to allow URL overrides (default: false)
     */
    private Boolean allowUrlOverride;

    /**
     * A function that takes {@link Experiment} and {@link ExperimentResult} as arguments.
     */
    @Nullable
    private TrackingCallback trackingCallback;

    /**
     * A function that takes {@link String} and {@link FeatureResult} as arguments.
     * A callback that will be invoked every time a feature is viewed. Listen for feature usage events
     */
    @Nullable
    private FeatureUsageCallback featureUsageCallback;

    /**
     * String format of user attributes that are used to assign variations
     */
    @Nullable
    private String attributesJson;

    /**
     * Helper class for differentiate whether specific experiment was evaluated before or not. Internal usage
     */
    private ExperimentHelper experimentHelper = new ExperimentHelper();

    /**
     * Once you define your Saved Groups, you can easily reference them from any Feature rule or Experiment.
     * Updates to saved groups apply immediately and will be instantly propagated to all matching Features and Experiments.
     * There are two types of Saved Groups:
     * ID Lists - Pick an attribute and define a list of values directly within the GrowthBook UI.
     * For example, you can make an Admin group and add the userId of all of your admins.
     * Condition Groups - Configure advanced targeting rules based on a user's attributes. For example,
     * "all users located in the US on a mobile device".
     */
    @Nullable
    private JsonObject savedGroups;

    /**
     * You can update the attributes JSON with new user attributes to evaluate against.
     *
     * @param attributesJson updated user attributes
     */
    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
        if (attributesJson != null) {
            this.setAttributes(TransformationUtil.transformAttributes(attributesJson));
        }
    }

    /**
     * Map of user attributes that are used to assign variations
     */
    @Nullable
    private JsonObject attributes;

    /**
     * You can update the attributes with new user attributes to evaluate against.
     *
     * @param attributes updated user attributes
     */
    public void setAttributes(@Nullable JsonObject attributes) {
        this.attributes = (attributes == null) ? new JsonObject() : attributes;
    }

    /**
     * Optional encryption key. If this is not null, featuresJson should be an encrypted payload.
     */
    @Nullable
    private String encryptionKey;

    /**
     * You can update the features JSON with new features to evaluate against.
     *
     * @param featuresJson updated features
     */

    public void setFeaturesJson(String featuresJson) {
        if (featuresJson != null) {
            this.setFeatures(TransformationUtil.transformFeatures(featuresJson));
        }
    }

    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    @Nullable
    private Map<String, Integer> forcedVariationsMap;

    /**
     * Service that provide functionality of Sticky Bucketing
     */
    @Nullable
    private StickyBucketService stickyBucketService;

    /**
     * Map of Sticky Bucket documents
     */
    @Nullable
    private Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs;

    /**
     * List of user's attributes keys
     */
    @Nullable
    private List<String> stickyBucketIdentifierAttributes;

    /**
     * The builder class to help create a context. You can use {@link #builder()} or the {@link GBContext} constructor
     */
    public static class GBContextBuilder {
    } // This stub is required for JavaDoc and is filled by Lombuk

    /**
     * The builder class to help create a context. You can use this builder or the constructor
     *
     * @return {@link CustomGBContextBuilder}
     */
    public static GBContextBuilder builder() {
        return new CustomGBContextBuilder();
    }

    static class CustomGBContextBuilder extends GBContextBuilder {
        @Override
        public GBContext build() {
            GBContext context = super.build();
            context.setAttributesJson(context.attributesJson);

            return context;
        }
    }
}
