package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
    String conditionJson;

    @Nullable
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
    @Builder.Default
    HashVersion hashVersion = HashVersion.V1;

    @Nullable
    @Builder.Default
    List<BucketRange> ranges = new ArrayList<>();

    @Nullable
    @Builder.Default
    List<VariationMeta> meta = new ArrayList<>();

    @Nullable
    @Builder.Default
    List<Filter> filters = new ArrayList<>();

    @Nullable
    String seed;

    @Nullable
    String name;

    @Nullable
    String phase;

    /**
     * Get a Gson JsonElement of the experiment
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
     * @param object experiment
     * @param <ValueType> value type for the experiment
     * @return JsonElement
     */
    public static <ValueType> JsonElement getJson(Experiment<ValueType> object) {
        JsonObject json = new JsonObject();

        json.addProperty("key", object.getKey());

        JsonElement variationsElement = GrowthBookJsonUtils.getJsonElementForObject(object.getVariations());
        json.add("variations", variationsElement);

        JsonElement weightsElement = GrowthBookJsonUtils.getJsonElementForObject(object.getWeights());
        json.add("weights", weightsElement);

        json.addProperty("isActive", object.getIsActive());
        json.addProperty("coverage", object.getCoverage());

        JsonElement namespaceElement = GrowthBookJsonUtils.getJsonElementForObject(object.getNamespace());
        json.add("namespace", namespaceElement);

        json.addProperty("force", object.getForce());
        json.addProperty("hashAttribute", object.getHashAttribute());

        return json;
    }

    /**
     * A Gson serializer for {@link Experiment}
     * @return a serializer for {@link Experiment}
     * @param <ValueType> value type for the Experiment
     */
    public static <ValueType> JsonSerializer<Experiment<ValueType>> getSerializer() {
        return new JsonSerializer<Experiment<ValueType>>() {
            @Override
            public JsonElement serialize(Experiment<ValueType> src, Type typeOfSrc, JsonSerializationContext context) {
                return Experiment.getJson(src);
            }
        };
    }

    // endregion Serialization
}
