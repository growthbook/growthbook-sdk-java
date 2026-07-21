package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonElement;
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
 * A single event dispatched by {@link GrowthBookTrackingPlugin} to the
 * GrowthBook data-warehouse ingest endpoint. Field names and shape mirror the
 * Go SDK's tracking plugin. Serialized by Gson; null fields are omitted, so
 * each event only carries the keys relevant to its {@link EventType}.
 */
@Slf4j
@Builder(access = AccessLevel.PRIVATE)
final class TrackingEvent {

    enum EventType {
        @SerializedName("experiment_viewed")
        EXPERIMENT_VIEWED,
        @SerializedName("feature_evaluated")
        FEATURE_EVALUATED
    }

    @SerializedName("event_type")
    private final EventType eventType;

    @SerializedName("timestamp")
    private final long timestamp;

    @SerializedName("sdk_language")
    private final String sdkLanguage;

    @SerializedName("sdk_version")
    private final String sdkVersion;

    @Nullable
    @SerializedName("experiment_id")
    private final String experimentId;

    @Nullable
    @SerializedName("experiment_name")
    private final String experimentName;

    @Nullable
    @SerializedName("variation_id")
    private final Integer variationId;

    @Nullable
    @SerializedName("variation_key")
    private final String variationKey;

    @Nullable
    @SerializedName("variation_value")
    private final JsonElement variationValue;

    @Nullable
    @SerializedName("in_experiment")
    private final Boolean inExperiment;

    @Nullable
    @SerializedName("hash_used")
    private final Boolean hashUsed;

    @Nullable
    @SerializedName("hash_attribute")
    private final String hashAttribute;

    @Nullable
    @SerializedName("hash_value")
    private final String hashValue;

    @Nullable
    @SerializedName("feature_id")
    private final String featureId;

    @Nullable
    @SerializedName("feature_key")
    private final String featureKey;

    @Nullable
    @SerializedName("feature_value")
    private final JsonElement featureValue;

    @Nullable
    @SerializedName("source")
    private final String source;

    @Nullable
    @SerializedName("on")
    private final Boolean on;

    @Nullable
    @SerializedName("off")
    private final Boolean off;

    @Nullable
    @SerializedName("rule_id")
    private final String ruleId;

    static <ValueType> TrackingEvent forExperiment(Experiment<ValueType> experiment, ExperimentResult<ValueType> result) {
        return TrackingEvent.builder()
                .eventType(EventType.EXPERIMENT_VIEWED)
                .timestamp(now())
                .sdkLanguage(SdkMetadata.LANGUAGE)
                .sdkVersion(SdkMetadata.VERSION)
                .experimentId(experiment != null ? experiment.getKey() : null)
                .experimentName(experiment != null ? emptyToNull(experiment.getName()) : null)
                .variationId(result != null ? result.getVariationId() : null)
                .variationKey(result != null ? result.getKey() : null)
                .variationValue(result != null ? toJson(result.getValue()) : null)
                .inExperiment(result != null ? result.getInExperiment() : null)
                .hashUsed(result != null ? result.getHashUsed() : null)
                .hashAttribute(result != null ? result.getHashAttribute() : null)
                .hashValue(result != null ? result.getHashValue() : null)
                .featureId(result != null ? emptyToNull(result.getFeatureId()) : null)
                .build();
    }

    static <ValueType> TrackingEvent forFeature(String featureKey, FeatureResult<ValueType> result) {
        TrackingEventBuilder builder = TrackingEvent.builder()
                .eventType(EventType.FEATURE_EVALUATED)
                .timestamp(now())
                .sdkLanguage(SdkMetadata.LANGUAGE)
                .sdkVersion(SdkMetadata.VERSION)
                .featureKey(featureKey);

        if (result != null) {
            builder.featureValue(toJson(result.getValue()))
                    .source(result.getSource() != null ? result.getSource().toString() : null)
                    .on(result.isOn())
                    .off(result.isOff())
                    .ruleId(emptyToNull(result.getRuleId()));

            Experiment<ValueType> experiment = result.getExperiment();
            if (experiment != null) {
                builder.experimentId(experiment.getKey());
            }
            ExperimentResult<ValueType> experimentResult = result.getExperimentResult();
            if (experimentResult != null) {
                builder.variationId(experimentResult.getVariationId());
            }
        }
        return builder.build();
    }

    private static long now() {
        return System.currentTimeMillis();
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
