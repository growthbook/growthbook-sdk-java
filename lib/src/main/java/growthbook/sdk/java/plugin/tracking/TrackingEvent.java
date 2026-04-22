package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.time.Instant;

/**
 * A single event dispatched by {@link GrowthBookTrackingPlugin} to the
 * GrowthBook data-warehouse ingest endpoint. Field names mirror the Go SDK.
 */
public final class TrackingEvent {

    public static final String EVENT_EXPERIMENT_VIEWED = "experiment_viewed";
    public static final String EVENT_FEATURE_EVALUATED = "feature_evaluated";

    @SerializedName("event_type")
    private final String eventType;

    @SerializedName("timestamp")
    private final String timestamp;

    @SerializedName("sdk_language")
    private final String sdkLanguage = SdkMetadata.LANGUAGE;

    @SerializedName("sdk_version")
    private final String sdkVersion = SdkMetadata.VERSION;

    @Nullable
    @SerializedName("attributes")
    private final JsonElement attributes;

    @Nullable
    @SerializedName("experiment_key")
    private final String experimentKey;

    @Nullable
    @SerializedName("variation_id")
    private final Integer variationId;

    @Nullable
    @SerializedName("variation_key")
    private final String variationKey;

    @Nullable
    @SerializedName("hash_attribute")
    private final String hashAttribute;

    @Nullable
    @SerializedName("hash_value")
    private final String hashValue;

    @Nullable
    @SerializedName("feature_key")
    private final String featureKey;

    @Nullable
    @SerializedName("feature_source")
    private final String featureSource;

    @Nullable
    @SerializedName("rule_id")
    private final String ruleId;

    @Nullable
    @SerializedName("value")
    private final JsonElement value;

    private TrackingEvent(Builder b) {
        this.eventType = b.eventType;
        this.timestamp = b.timestamp != null ? b.timestamp : Instant.now().toString();
        this.attributes = b.attributes;
        this.experimentKey = b.experimentKey;
        this.variationId = b.variationId;
        this.variationKey = b.variationKey;
        this.hashAttribute = b.hashAttribute;
        this.hashValue = b.hashValue;
        this.featureKey = b.featureKey;
        this.featureSource = b.featureSource;
        this.ruleId = b.ruleId;
        this.value = b.value;
    }

    public String getEventType() {
        return eventType;
    }

    public static <V> TrackingEvent forExperiment(
            Experiment<V> experiment,
            ExperimentResult<V> result,
            @Nullable JsonElement attributes
    ) {
        return new Builder()
                .eventType(EVENT_EXPERIMENT_VIEWED)
                .attributes(attributes)
                .experimentKey(experiment != null ? experiment.getKey() : null)
                .variationId(result != null ? result.getVariationId() : null)
                .variationKey(result != null ? result.getKey() : null)
                .hashAttribute(result != null ? result.getHashAttribute() : null)
                .hashValue(result != null ? result.getHashValue() : null)
                .value(result != null ? toJson(result.getValue()) : null)
                .build();
    }

    public static <V> TrackingEvent forFeature(
            String featureKey,
            FeatureResult<V> result,
            @Nullable JsonElement attributes
    ) {
        String source = null;
        String ruleId = null;
        String experimentKey = null;
        Integer variationId = null;
        Object value = null;
        if (result != null) {
            if (result.getSource() != null) {
                source = result.getSource().toString();
            }
            ruleId = result.getRuleId();
            value = result.getValue();
            Experiment<V> exp = result.getExperiment();
            ExperimentResult<V> expResult = result.getExperimentResult();
            if (exp != null) {
                experimentKey = exp.getKey();
            }
            if (expResult != null) {
                variationId = expResult.getVariationId();
            }
        }
        return new Builder()
                .eventType(EVENT_FEATURE_EVALUATED)
                .attributes(attributes)
                .featureKey(featureKey)
                .featureSource(source)
                .ruleId(ruleId)
                .experimentKey(experimentKey)
                .variationId(variationId)
                .value(toJson(value))
                .build();
    }

    @Nullable
    private static JsonElement toJson(@Nullable Object value) {
        if (value == null) return null;
        try {
            return GrowthBookJsonUtils.getInstance().gson.toJsonTree(value);
        } catch (Throwable t) {
            return null;
        }
    }

    static final class Builder {
        private String eventType;
        private String timestamp;
        private JsonElement attributes;
        private String experimentKey;
        private Integer variationId;
        private String variationKey;
        private String hashAttribute;
        private String hashValue;
        private String featureKey;
        private String featureSource;
        private String ruleId;
        private JsonElement value;

        Builder eventType(String v) { this.eventType = v; return this; }
        Builder attributes(JsonElement v) { this.attributes = v; return this; }
        Builder experimentKey(String v) { this.experimentKey = v; return this; }
        Builder variationId(Integer v) { this.variationId = v; return this; }
        Builder variationKey(String v) { this.variationKey = v; return this; }
        Builder hashAttribute(String v) { this.hashAttribute = v; return this; }
        Builder hashValue(String v) { this.hashValue = v; return this; }
        Builder featureKey(String v) { this.featureKey = v; return this; }
        Builder featureSource(String v) { this.featureSource = v; return this; }
        Builder ruleId(String v) { this.ruleId = v; return this; }
        Builder value(JsonElement v) { this.value = v; return this; }

        TrackingEvent build() { return new TrackingEvent(this); }
    }
}
