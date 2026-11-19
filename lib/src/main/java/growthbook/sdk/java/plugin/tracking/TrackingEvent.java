package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * A single event dispatched by {@link GrowthBookTrackingPlugin} to the GrowthBook ingest
 * {@code /track} endpoint. The shape mirrors the JS and Go SDKs: a human-readable
 * {@code event_name}, a {@code properties} map, the user's {@code attributes} at evaluation time,
 * and the SDK language/version. Serialized by Gson; null fields are omitted.
 */
@Slf4j
@Builder(access = AccessLevel.PRIVATE)
final class TrackingEvent {

    static final String EXPERIMENT_VIEWED = "Experiment Viewed";
    static final String FEATURE_EVALUATED = "Feature Evaluated";

    @SerializedName("event_name")
    private final String eventName;

    @SerializedName("properties")
    private final JsonObject properties;

    @Nullable
    @SerializedName("attributes")
    private final JsonObject attributes;

    @SerializedName("sdk_language")
    private final String sdkLanguage;

    @SerializedName("sdk_version")
    private final String sdkVersion;

    static <ValueType> TrackingEvent forExperiment(Experiment<ValueType> experiment,
                                                   ExperimentResult<ValueType> result,
                                                   @Nullable JsonObject userAttributes) {
        JsonObject properties = new JsonObject();
        putString(properties, "experimentId", experiment != null ? experiment.getKey() : null);
        putString(properties, "variationId", result != null ? result.getKey() : null);
        putString(properties, "hashAttribute", result != null ? result.getHashAttribute() : null);
        putString(properties, "hashValue", result != null ? result.getHashValue() : null);

        return TrackingEvent.builder()
                .eventName(EXPERIMENT_VIEWED)
                .properties(properties)
                .attributes(snapshot(userAttributes))
                .sdkLanguage(SdkMetadata.LANGUAGE)
                .sdkVersion(SdkMetadata.VERSION)
                .build();
    }

    static <ValueType> TrackingEvent forFeature(String featureKey,
                                                FeatureResult<ValueType> result,
                                                @Nullable JsonObject userAttributes) {
        JsonObject properties = new JsonObject();
        putString(properties, "feature", featureKey);
        if (result != null) {
            putElement(properties, "value", toJson(result.getValue()));
            putString(properties, "source", result.getSource() != null ? result.getSource().toString() : null);
            putString(properties, "ruleId", emptyToNull(result.getRuleId()));
            ExperimentResult<ValueType> experimentResult = result.getExperimentResult();
            if (experimentResult != null) {
                putString(properties, "variationId", experimentResult.getKey());
            }
        }

        return TrackingEvent.builder()
                .eventName(FEATURE_EVALUATED)
                .properties(properties)
                .attributes(snapshot(userAttributes))
                .sdkLanguage(SdkMetadata.LANGUAGE)
                .sdkVersion(SdkMetadata.VERSION)
                .build();
    }

    private static void putString(JsonObject target, String key, @Nullable String value) {
        if (value != null) {
            target.addProperty(key, value);
        }
    }

    private static void putElement(JsonObject target, String key, @Nullable JsonElement value) {
        if (value != null && !value.isJsonNull()) {
            target.add(key, value);
        }
    }

    @Nullable
    private static JsonObject snapshot(@Nullable JsonObject attributes) {
        return attributes == null ? null : attributes.deepCopy();
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Nullable
    private static JsonElement toJson(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return GrowthBookJsonUtils.getInstance().gson.toJsonTree(value);
        } catch (Exception e) {
            log.debug("Failed to serialize tracking event value; dropping it: {}", e.toString());
            return null;
        }
    }
}
