package growthbook.sdk.java;

import com.google.gson.JsonElement;
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
    JsonElement conditionJson;

    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    @Nullable
    ArrayList<ParentCondition> parentConditions;

    @Nullable
    @Deprecated
    Namespace namespace;

    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    Integer force;

    /**
     * What user attribute should be used to assign variations (defaults to `id`)
     */
    @Builder.Default
    String hashAttribute = "id";

    @Nullable
    Integer hashVersion;

    @Nullable
    ArrayList<BucketRange> ranges;

    @Nullable
    @SerializedName("meta")
    ArrayList<VariationMeta> meta;

    @Nullable
    ArrayList<Filter> filters;

    @Nullable
    String seed;

    @Nullable
    String name;

    @Nullable
    String phase;

    @Nullable
    String fallbackAttribute;

    @Nullable
    Boolean disableStickyBucketing;

    @Nullable
    Integer bucketVersion;

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
