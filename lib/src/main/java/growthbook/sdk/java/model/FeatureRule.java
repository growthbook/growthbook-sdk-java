package growthbook.sdk.java.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
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
@NoArgsConstructor
public class FeatureRule<ValueType> implements JsonDeserializer<FeatureRule<ValueType>> {
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
    OptionalField<ValueType> force;

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

    /**
     * Array of filters to apply to the rule
     */
    @Nullable
    ArrayList<Filter> filters;

    /**
     * Seed to use for hashing
     */
    @Nullable
    String seed;

    /**
     * Human-readable name for the experiment
     */
    @Nullable
    String name;

    /**
     * The phase id of the experiment
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
     * (Note: sticky bucketing is only available if a StickyBucketingService is provided in the Context)
     */
    @Nullable
    Boolean disableStickyBucketing;

    /**
     * An sticky bucket version number that can be used to force a re-bucketing of users (default to 0)
     */
    @Nullable
    Integer bucketVersion;

    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    @Nullable
    Integer minBucketVersion;

    /**
     * Array of tracking calls to fire
     */
    @Nullable
    ArrayList<TrackData<ValueType>> tracks;

    @Override
    public FeatureRule<ValueType> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject jsonObject = json.getAsJsonObject();
        FeatureRule.FeatureRuleBuilder<ValueType> builder = FeatureRule.builder();

        builder.id(jsonObject.has("id") ? context.deserialize(jsonObject.get("id"), String.class) : null);
        builder.key(jsonObject.has("key") ? context.deserialize(jsonObject.get("key"), String.class) : null);
        builder.coverage(jsonObject.has("coverage") ? context.deserialize(jsonObject.get("coverage"), Float.class) : null);


        if (jsonObject.has("force")) {
            JsonElement forceElement = jsonObject.get("force");
            if (!forceElement.isJsonNull()) {
                ValueType forceValue = context.deserialize(forceElement, new TypeToken<ValueType>() {}.getType());
                builder.force(new OptionalField<>(true, forceValue));
            } else {
                builder.force(new OptionalField<>(true, null));
            }
        } else {
            builder.force(new OptionalField<>(false, null));
        }

        if (jsonObject.has("variations")) {
            builder.variations(context.deserialize(jsonObject.get("variations"), new TypeToken<ArrayList<ValueType>>() {}.getType()));
        }

        if (jsonObject.has("weights")) {
            builder.weights(context.deserialize(jsonObject.get("weights"), new TypeToken<ArrayList<Float>>() {}.getType()));
        }

        builder.namespace(jsonObject.has("namespace") ? context.deserialize(jsonObject.get("namespace"), Namespace.class) : null);
        builder.hashAttribute(jsonObject.has("hashAttribute") ? context.deserialize(jsonObject.get("hashAttribute"), String.class) : "id");
        builder.condition(jsonObject.has("condition") ? context.deserialize(jsonObject.get("condition"), JsonObject.class) : null);

        if (jsonObject.has("parentConditions")) {
            builder.parentConditions(context.deserialize(jsonObject.get("parentConditions"), new TypeToken<ArrayList<ParentCondition>>() {}.getType()));
        }

        builder.hashVersion(jsonObject.has("hashVersion") ? context.deserialize(jsonObject.get("hashVersion"), Integer.class) : null);
        builder.range(jsonObject.has("range") ? context.deserialize(jsonObject.get("range"), BucketRange.class) : null);

        if (jsonObject.has("ranges")) {
            builder.ranges(context.deserialize(jsonObject.get("ranges"), new TypeToken<ArrayList<BucketRange>>() {}.getType()));
        }

        if (jsonObject.has("meta")) {
            builder.meta(context.deserialize(jsonObject.get("meta"), new TypeToken<ArrayList<VariationMeta>>() {}.getType()));
        }

        if (jsonObject.has("filters")) {
            builder.filters(context.deserialize(jsonObject.get("filters"), new TypeToken<ArrayList<Filter>>() {}.getType()));
        }

        builder.seed(jsonObject.has("seed") ? context.deserialize(jsonObject.get("seed"), String.class) : null);
        builder.name(jsonObject.has("name") ? context.deserialize(jsonObject.get("name"), String.class) : null);
        builder.phase(jsonObject.has("phase") ? context.deserialize(jsonObject.get("phase"), String.class) : null);
        builder.fallbackAttribute(jsonObject.has("fallbackAttribute") ? context.deserialize(jsonObject.get("fallbackAttribute"), String.class) : null);
        builder.disableStickyBucketing(jsonObject.has("disableStickyBucketing") ? context.deserialize(jsonObject.get("disableStickyBucketing"), Boolean.class) : null);
        builder.bucketVersion(jsonObject.has("bucketVersion") ? context.deserialize(jsonObject.get("bucketVersion"), Integer.class) : null);
        builder.minBucketVersion(jsonObject.has("minBucketVersion") ? context.deserialize(jsonObject.get("minBucketVersion"), Integer.class) : null);

        if (jsonObject.has("tracks")) {
            builder.tracks(context.deserialize(jsonObject.get("tracks"), new TypeToken<ArrayList<TrackData<ValueType>>>() {}.getType()));
        }

        return builder.build();
    }
}
