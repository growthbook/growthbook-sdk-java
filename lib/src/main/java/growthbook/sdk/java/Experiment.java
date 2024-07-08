package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * Defines a single Experiment
 */
@Data
@Builder
@AllArgsConstructor
public class Experiment<ValueType> {
    /**
     * The globally unique identifier for the experiment
     */
    String key;

    /**
     * The different variations to choose between
     */
    @Builder.Default
    ArrayList<ValueType> variations = new ArrayList<>();

    /**
     * How to weight traffic between variations. Must add to 1.
     */
    @Nullable
    ArrayList<Float> weights;

    /**
     * If set to false, always return the control (first variation)
     */
    @Nullable
    @SerializedName("active")
    Boolean isActive;

    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    Float coverage;

    /**
     * Optional targeting condition
     */
    JsonObject conditionJson;

    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    @Nullable
    ArrayList<ParentCondition> parentConditions;

    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment
     */
    @Nullable
    @Deprecated
    Namespace namespace;

    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    Integer force;

    /**
     * What user attribute should be used to assign variations (defaults to `id`)
     * All users included in the experiment will be forced into the specific variation index
     */
    @Builder.Default
    String hashAttribute = "id";

    //new properties v0.4.0
    /**
     * The hash version to use (default to 1)
     */
    @Nullable
    Integer hashVersion;

    /**
     * Array of ranges, one per variation
     */
    @Nullable
    ArrayList<BucketRange> ranges;

    /**
     * Meta info about the variations
     */
    @Nullable
    @SerializedName("meta")
    ArrayList<VariationMeta> meta;

    /**
     * Array of filters to apply
     */
    @Nullable
    ArrayList<Filter> filters;

    /**
     * The hash seed to use
     */
    @Nullable
    String seed;

    /**
     * Human-readable name for the experiment
     */
    @Nullable
    String name;

    /**
     * Identifier of the current experiment phase
     */
    @Nullable
    String phase;

    /**
     * When using sticky bucketing, can be used as a fallback to assign variations
     */
    @Nullable
    String fallbackAttribute;

    /**
     * If true, sticky bucketing will be disabled for this experiment.
     * (Note: sticky bucketing is only available
     * if a StickyBucketingService is provided in the Context)
     */
    @Nullable
    Boolean disableStickyBucketing;

    /**
     * The sticky bucket version number that can be used to force a re-bucketing
     * of users (default to 0)
     */
    @Nullable
    Integer bucketVersion;

    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    @Nullable
    Integer minBucketVersion;

    /**
     * Get a Gson JsonElement of the experiment
     *
     * @return JsonElement
     */
    public String toJson() {
        return Experiment.getJson(this).toString();
    }

    @Override
    public String toString() {
        return this.toJson();
    }


    // region Serialization

    /**
     * Get a Gson JsonElement of the experiment
     *
     * @param object      experiment
     * @param <ValueType> value type for the experiment
     * @return JsonElement
     */
    public static <ValueType> JsonElement getJson(Experiment<ValueType> object) {
        return GrowthBookJsonUtils.getJsonElementForObject(object);
    }

    // endregion Serialization
}
