package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * The result of an {@link GrowthBook#run(Experiment)} call
 * @param <ValueType> generic type for the value type for this experiment's variations.
 */
@Data
public class ExperimentResult<ValueType> {
    @Nullable
    ValueType value;

    @Nullable
    Integer variationId;

    Boolean inExperiment;

    @Nullable
    String hashAttribute;

    @Nullable
    String hashValue;

    @Nullable
    String featureId;

    Boolean hashUsed;

    @Nullable
    String key;

    @Nullable
    String name;

    @Nullable
    Float bucket;

    @Nullable
    @SerializedName("passthrough")
    Boolean passThrough;

    @Nullable
    Boolean stickyBucketUsed;

    /**
     * The result of running an {@link Experiment} given a specific {@link GBContext}
     *
     * @param value The array value of the assigned variation
     * @param variationId The array index of the assigned variation
     * @param inExperiment Whether the user is part of the experiment or not
     * @param hashAttribute The user attribute used to assign a variation (default: "id")
     * @param hashValue The value of that attribute
     * @param featureId The id of the feature (if any) that the experiment came from
     * @param hashUsed If a hash was used to assign a variation
     * @param key The experiment key, if any
     * @param name The human-readable name of the assigned variation
     * @param bucket The hash value used to assign a variation (float from 0 to 1)
     * @param passThrough Used for holdout groups
     */
    @Builder
    public ExperimentResult(
        @Nullable ValueType value,
        @Nullable Integer variationId,
        Boolean inExperiment,
        @Nullable String hashAttribute,
        @Nullable String hashValue,
        @Nullable String featureId,
        Boolean hashUsed,
        @Nullable String key,
        @Nullable String name,
        @Nullable Float bucket,
        @Nullable Boolean passThrough,
        @Nullable Boolean stickyBucketUsed
    ) {
        this.value = value;
        this.variationId = variationId;
        this.inExperiment = inExperiment == null ? false : inExperiment;
        this.hashAttribute = hashAttribute == null ? "id" : hashAttribute;
        this.hashValue = hashValue;
        this.featureId = featureId;
        this.hashUsed = hashUsed == null ? false : hashUsed;

        this.key = key;
        if (this.key == null && variationId != null) {
            this.key = variationId.toString();
        }

        this.name = name;
        this.bucket = bucket;
        this.passThrough = passThrough;
        this.stickyBucketUsed = stickyBucketUsed;
    }

    // region Serialization

    /**
     * Serialized JSON string of the {@link ExperimentResult}
     * @return JSON string
     */
    public String toJson() {
        return ExperimentResult.getJson(this).toString();
    }

    static <ValueType> JsonElement getJson(ExperimentResult<ValueType> object) {
        return GrowthBookJsonUtils.getJsonElementForObject(object);
    }

    // endregion Serialization
}
