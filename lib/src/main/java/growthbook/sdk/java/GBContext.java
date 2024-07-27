package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.stickyBucketing.StickyAssignmentsDocument;
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
            this.features = transformEncryptedFeatures(featuresJson, encryptionKey);
        }
        if (features != null) {
            this.features = features;
        }

        this.enabled = enabled == null ? true : enabled;
        this.isQaMode = isQaMode == null ? false : isQaMode;
        this.allowUrlOverride = allowUrlOverrides == null ? false : allowUrlOverrides;
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

    private ExperimentHelper experimentHelper = new ExperimentHelper();

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
            this.setAttributes(GBContext.transformAttributes(attributesJson));
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
            this.setFeatures(GBContext.transformFeatures(featuresJson));
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

    @Nullable
    public static JsonObject transformFeatures(String featuresJsonString) {
        try {
            return GrowthBookJsonUtils.getInstance().gson.fromJson(featuresJsonString, JsonObject.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public static JsonObject transformEncryptedFeatures(
            @Nullable String featuresJson,
            @Nullable String encryptionKey
    ) {
        // Features start as empty JSON
        JsonObject jsonObject = new JsonObject();

        if (encryptionKey != null && featuresJson != null) {
            // Attempt to decrypt payload
            try {
                String decrypted = DecryptionUtils.decrypt(featuresJson, encryptionKey);
                String featuresJsonDecrypted = decrypted.trim();
                jsonObject = GBContext.transformFeatures(featuresJsonDecrypted);

            } catch (DecryptionUtils.DecryptionException e) {
                log.error(e.getMessage(), e);

            }
        } else if (featuresJson != null) {
            jsonObject = GBContext.transformFeatures(featuresJson);
        }

        return jsonObject;
    }

    private static JsonObject transformAttributes(@Nullable String attributesJsonString) {
        try {
            if (attributesJsonString == null) {
                return new JsonObject();
            }

            JsonElement element = GrowthBookJsonUtils.getInstance().gson.fromJson(attributesJsonString, JsonElement.class);
            if (element == null || element.isJsonNull()) {
                return new JsonObject();
            }

            return GrowthBookJsonUtils.getInstance().gson.fromJson(attributesJsonString, JsonObject.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new JsonObject();
        }
    }
}
