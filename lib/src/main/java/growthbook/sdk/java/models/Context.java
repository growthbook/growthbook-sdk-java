package growthbook.sdk.java.models;

import com.google.gson.JsonElement;
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
    private HashMap<String, Feature> features;

    /**
     * Switch to globally disable all experiments
     */
    @Builder.Default
    Boolean enabled = true;

    /**
     * The URL of the current page
     */
    @Nullable
    String url;

    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    @Nullable
    @Builder.Default
    Boolean isQaMode = false;

    /**
     * A function that takes `experiment` and `result` as arguments.
     */
    @Nullable
    TrackingCallback trackingCallback;

    /**
     * Map of user attributes that are used to assign variations
     */
    @Nullable
    HashMap<String, String> attributes;

    // TODO: Features
    /**
     * Feature definitions
     */
    @Nullable
    private String featuresJson = "{}";

    // TODO: Would this be more user-friendly as its own type ForcedVariationsMap or Map<String, Integer> ?
    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    @Nullable
    @Builder.Default
    Map<String, Integer> forcedVariationsMap = new HashMap<>();
//    ForcedVariationsMap forcedVariationsMap;

    public void setFeatures(String featuresJson) {
        this.featuresJson = featuresJson;
        this.features = Context.transformFeatures(featuresJson);
    }

    static HashMap<String, Feature> transformFeatures(String featuresJsonString) {
        HashMap<String, Feature> transformedFeatures = new HashMap<>();

        JsonElement featuresJson = GrowthBookJsonUtils.getInstance().gson.fromJson(featuresJsonString, JsonElement.class);

        for (Map.Entry<String, JsonElement> entry : featuresJson.getAsJsonObject().entrySet()) {
            // TODO: Transform features
            Feature feature = new Feature(entry.getValue().toString());
            transformedFeatures.put(entry.getKey(), feature);
        }

        return transformedFeatures;
    }
}
