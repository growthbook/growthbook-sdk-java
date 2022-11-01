package growthbook.sdk.java.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.internal.services.GrowthBookJsonUtils;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object passed into the GrowthBook constructor.
 * The {@link Context#builder()} is recommended for constructing a Context.
 * Alternatively, you can use the static {@link #create(String, String, Boolean, Boolean, String, Map, TrackingCallback)} method.
 */
@Data @Builder
public class Context {

    /**
     * The {@link Context.ContextBuilder} is recommended for constructing a Context.
     * Alternatively, you can use this static method instead of the builder.
     * @param attributesJson User attributes as JSON string
     * @param featuresJson Features response as JSON string
     * @param isEnabled Whether globally all experiments are enabled. Defaults to true.
     * @param isQaMode If true, random assignment is disabled and only explicitly forced variations are used.
     * @param url A URL string
     * @param forcedVariationsMap Force specific experiments to always assign a specific variation (used for QA)
     * @param trackingCallback A function that takes {@link Experiment} and {@link ExperimentResult} as arguments.
     * @return created context
     */
    public static Context create(
            @Nullable String attributesJson,
            @Nullable String featuresJson,
            @Nullable Boolean isEnabled,
            Boolean isQaMode,
            @Nullable String url,
            @Nullable Map<String, Integer> forcedVariationsMap,
            @Nullable TrackingCallback trackingCallback
    ) {
        return Context
                .builder()
                .attributesJson(attributesJson)
                .featuresJson(featuresJson)
                .enabled(isEnabled)
                .isQaMode(isQaMode)
                .url(url)
                .forcedVariationsMap(forcedVariationsMap)
                .trackingCallback(trackingCallback)
                .build();
    }

    @Nullable
    private JsonObject features;

    private void setFeatures(@Nullable JsonObject features) {
        this.features = features;
    }

    @Nullable
    @Builder.Default
    private Boolean enabled = true;

    @Nullable
    private String url;

    @Builder.Default
    private Boolean isQaMode = false;

    @Nullable
    private TrackingCallback trackingCallback;

    @Nullable
    @Builder.Default
    private String attributesJson = "{}";

    /**
     * You can update the attributes JSON with new user attributes to evaluate against.
     * @param attributesJson updated user attributes
     */
    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
        if (attributesJson != null) {
            this.setAttributes(Context.transformAttributes(attributesJson));
        }
    }

    @Nullable
    private JsonObject attributes;

    private void setAttributes(@Nullable JsonObject attributes) {
        this.attributes = attributes;
    }

    @Nullable
    @Builder.Default
    private String featuresJson = "{}";

    /**
     * You can update the features JSON with new features to evaluate against.
     * @param featuresJson updated features
     */

    public void setFeaturesJson(String featuresJson) {
        this.featuresJson = featuresJson;
        if (featuresJson != null) {
            this.setFeatures(Context.transformFeatures(featuresJson));
        }
    }

    @Nullable
    @Builder.Default
    private Map<String, Integer> forcedVariationsMap = new HashMap<>();

    /**
     * The builder class to help create a context. You can use {@link #builder()} or {@link #create(String, String, Boolean, Boolean, String, Map, TrackingCallback)} to create a {@link Context}
     */
    public static class ContextBuilder {} // This stub is required for JavaDoc and is filled by Lombuk

    /**
     * The builder class to help create a context. You can use this or {@link #create(String, String, Boolean, Boolean, String, Map, TrackingCallback)} to create a {@link Context}
     * @return {@link CustomContextBuilder}
     */
    public static ContextBuilder builder() {
        return new CustomContextBuilder();
    }

    static class CustomContextBuilder extends ContextBuilder {
        @Override
        public Context build() {
            Context context = super.build();

            if (context.featuresJson != null) {
                context.setFeatures(Context.transformFeatures(context.featuresJson));
            }

            context.setAttributesJson(context.attributesJson);

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
            e.printStackTrace();
            return new JsonObject();
        }
    }
}
