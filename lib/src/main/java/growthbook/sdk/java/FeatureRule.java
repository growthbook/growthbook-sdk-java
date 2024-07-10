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
 * Overrides the defaultValue of a Feature based on a set of requirements. Has a number of optional properties
 *
 * <ul>
 * <li>condition (Condition) - Optional targeting condition</li>
 * <li>coverage (number) - What percent of users should be included in the experiment (between 0 and 1, inclusive)</li>
 * <li>force (any) - Immediately force a specific value (ignore every other option besides condition and coverage)</li>
 * <li>variations (any[]) - Run an experiment (A/B test) and randomly choose between these variations</li>
 * <li>key (string) - The globally unique tracking key for the experiment (default to the feature key)</li>
 * <li>weights (number[]) - How to weight traffic between variations. Must add to 1.</li>
 * <li>namespace (Namespace) - Adds the experiment to a namespace</li>
 * <li>hashAttribute (string) - What user attribute should be used to assign variations (defaults to id)</li>
 * </ul>
 *
 * @param <ValueType> generic type for the value type for this experiment's variations.
 */
@Data
@Builder
@AllArgsConstructor
public class FeatureRule<ValueType> {
    /**
     * Unique feature rule id
     */
    @Nullable
    String id;

    /**
     * The globally unique tracking key for the experiment (default to the feature key)
     */
    @Nullable
    String key;

    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    @Nullable
    Float coverage;

    /**
     * Immediately force a specific value (ignore every other option besides condition and coverage)
     */
    @Nullable
    ValueType force;

    /**
     * Run an experiment (A/B test) and randomly choose between these variations
     */
    @Nullable
    ArrayList<ValueType> variations;

    /**
     * How to weight traffic between variations. Must add to 1.
     */
    @Nullable
    ArrayList<Float> weights;

    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment.
     */
    @Nullable
    @Deprecated
    Namespace namespace;

    /**
     * What user attribute should be used to assign variations (defaults to id)
     */
    @Builder.Default
    String hashAttribute = "id";

    /**
     * Optional targeting condition
     */
    @Nullable
    JsonObject condition;

    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    @Nullable
    ArrayList<ParentCondition> parentConditions;

    // new properties v0.4.0
    /**
     * The hash version to use (default to 1)
     */
    @Nullable
    Integer hashVersion;

    /**
     * A more precise version of coverage
     */
    @Nullable
    BucketRange range;

    /**
     * Ranges for experiment variations
     */
    @Nullable
    ArrayList<BucketRange> ranges;

    /**
     * Meta info about the experiment variations
     */
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

    @Nullable
    ArrayList<TrackData<ValueType>> tracks;
}
