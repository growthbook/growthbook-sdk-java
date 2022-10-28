package growthbook.sdk.java.models;

import com.google.gson.JsonElement;
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

    @Nullable
    @Builder.Default
    private String attributesJson = "{}";

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
