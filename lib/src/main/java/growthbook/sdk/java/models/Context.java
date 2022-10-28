package growthbook.sdk.java.models;

import com.google.gson.JsonObject;
import growthbook.sdk.java.services.GrowthBookJsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object passed into the GrowthBook constructor.
 */
@Data
@Builder
@AllArgsConstructor
public class Context {
    @Nullable
    private JsonObject features;

    private void setFeatures(@Nullable JsonObject features) {
        this.features = features;
    }

    @Builder.Default
    private Boolean enabled = true;

    /**
     * The URL of the current page
     */
    @Nullable
    private String url;

    @Builder.Default
    private Boolean isQaMode = false;

    /**
     * A function that takes `experiment` and `result` as arguments.
     */
    @Nullable
    private TrackingCallback trackingCallback;

    /**
     * Map of user attributes that are used to assign variations
     */
    @Nullable
    private HashMap<String, Object> attributes;

    // TODO: Features
    /**
     * Feature definitions
     */
    @Nullable
    @Builder.Default
    private String featuresJson = "{}";

    public void setFeaturesJson(String featuresJson) {
        this.featuresJson = featuresJson;
        if (featuresJson != null) {
            this.setFeatures(Context.transformFeatures(featuresJson));
        }
    }

    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    @Nullable
    @Builder.Default
    private Map<String, Integer> forcedVariationsMap = new HashMap<>();

    public static ContextBuilder builder() {
        return new CustomContextBuilder();
    }

    public static class CustomContextBuilder extends ContextBuilder {
        @Override
        public Context build() {
            Context context = super.build();

            if (context.featuresJson != null) {
                context.setFeatures(Context.transformFeatures(context.featuresJson));
            }

            return context;
        }
    }

    @Nullable
    private static JsonObject transformFeatures(String featuresJsonString) {
        try {
            return GrowthBookJsonUtils.getInstance().gson.fromJson(featuresJsonString, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
//public class Context {
//    private HashMap<String, Feature> features;
//
//    public HashMap<String, Feature> getFeatures() {
//        return this.features;
//    }
//
//    public Context(
//            Boolean enabled,
//            HashMap<String, String> attributes,
//            String url,
//            @Nullable String featuresJson,
//            Map<String, Integer> forcedVariationsMap,
//            Boolean isQaMode,
//            TrackingCallback trackingCallback
//    ) {
//        this.enabled = enabled;
//        this.attributes = attributes;
//        this.url = url;
//        this.featuresJson = featuresJson;
//        this.forcedVariationsMap = forcedVariationsMap;
//        this.features = Context.transformFeatures(featuresJson);
//        this.isQaMode = isQaMode;
//        this.trackingCallback = trackingCallback;
//    }
//
//    /**
//     * Switch to globally disable all experiments
//     */
////    @Builder.Default
//    private Boolean enabled = true;
//
//    public Boolean getEnabled() {
//        return this.enabled;
//    }
//
//    public void setEnabled(Boolean enabled) {
//        this.enabled = enabled;
//    }
//
//    /**
//     * The URL of the current page
//     */
//    @Nullable
//    private String url;
//
//    public void setUrl(@Nullable String url) {
//        this.url = url;
//    }
//
//    @Nullable
//    public String getUrl() {
//        return this.url;
//    }
//
//    /**
//     * If true, random assignment is disabled and only explicitly forced variations are used.
//     */
////    @Builder.Default
//    private Boolean isQaMode = false;
//
//    public Boolean getIsQaMode() {
//        return this.isQaMode;
//    }
//
//    public void setIsQaMode(Boolean isQaMode) {
//        this.isQaMode = isQaMode;
//    }
//
//    /**
//     * A function that takes `experiment` and `result` as arguments.
//     */
//    @Nullable
//    private TrackingCallback trackingCallback;
//
//    @Nullable
//    public TrackingCallback getTrackingCallback() {
//        return this.trackingCallback;
//    }
//
//    public void setTrackingCallback(@Nullable TrackingCallback callback) {
//        this.trackingCallback = callback;
//    }
//
//    /**
//     * Map of user attributes that are used to assign variations
//     */
//    @Nullable
//    private HashMap<String, String> attributes;
//
//    @Nullable
//    public HashMap<String, String> getAttributes() {
//        return this.attributes;
//    }
//
//
//    // TODO: Features
//    /**
//     * Feature definitions
//     */
//    @Nullable
////    @Builder.Default
//    private String featuresJson = "{}";

//    /**
//     * Force specific experiments to always assign a specific variation (used for QA)
//     */
////    @Nullable
////    @Builder.Default
//    private Map<String, Integer> forcedVariationsMap = new HashMap<>();
//
//    public Map<String, Integer> getForcedVariationsMap() {
//        return this.forcedVariationsMap;
//    }
//
//    public void setFeatures(String featuresJson) {
//        this.featuresJson = featuresJson;
//        this.features = Context.transformFeatures(featuresJson);
//    }
//
//    public String getFeaturesJson() {
//        return this.featuresJson;
//    }
//
//    private static HashMap<String, Feature> transformFeatures(String featuresJsonString) {
//        HashMap<String, Feature> transformedFeatures = new HashMap<>();
//
//        JsonElement featuresJson = GrowthBookJsonUtils.getInstance().gson.fromJson(featuresJsonString, JsonElement.class);
//
//        for (Map.Entry<String, JsonElement> entry : featuresJson.getAsJsonObject().entrySet()) {
//            // TODO: Transform features
//            Feature feature = new Feature(entry.getValue().toString());
//            transformedFeatures.put(entry.getKey(), feature);
//        }
//
//        return transformedFeatures;
//    }
//}
