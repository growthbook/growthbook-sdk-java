package growthbook.sdk.java.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import growthbook.sdk.java.internal.services.GrowthBookJsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

/**
 * The result of running an {@link Experiment} given a specific {@link GBContext}
 *
 * <ul>
 * <li>inExperiment (boolean) - Whether or not the user is part of the experiment</li>
 * <li>variationId (int) - The array index of the assigned variation</li>
 * <li>value (any) - The array value of the assigned variation</li>
 * <li>hashUsed (boolean) - If a hash was used to assign a variation</li>
 * <li>hashAttribute (string) - The user attribute used to assign a variation</li>
 * <li>hashValue (string) - The value of that attribute</li>
 * <li>featureId (string or null) - The id of the feature (if any) that the experiment came from</li>
 * </ul>
 *
 * @param <ValueType> generic type for the value type for this experiment's variations.
 */
@Data
@Builder
@AllArgsConstructor
public class ExperimentResult<ValueType> {
    @Nullable
    ValueType value;

    @Nullable
    Integer variationId;

    @Builder.Default
    Boolean inExperiment = false;

    @Nullable
    @Builder.Default
    String hashAttribute = "id";

    @Nullable
    String hashValue;

    @Nullable
    String featureId;

    @Builder.Default
    Boolean hashUsed = false;


    // region Serialization

    /**
     * Serialized JSON string of the {@link ExperimentResult}
     * @return JSON string
     */
    public String toJson() {
        return ExperimentResult.getJson(this).toString();
    }

    static <ValueType> JsonElement getJson(ExperimentResult<ValueType> object) {
        JsonObject json = new JsonObject();

        json.addProperty("featureId", object.getFeatureId());

        JsonElement valueElement = GrowthBookJsonUtils.getJsonElementForObject(object.getValue());
        json.add("value", valueElement);

        json.addProperty("variationId", object.getVariationId());
        json.addProperty("inExperiment", object.getInExperiment());
        json.addProperty("hashUsed", object.getHashUsed());
        json.addProperty("hashAttribute", object.getHashAttribute());
        json.addProperty("hashValue", object.getHashValue());

        return json;
    }

    /**
     * A Gson serializer for {@link ExperimentResult}
     * @return a Gson serializer
     * @param <ValueType> type of the experiment
     */
    public static <ValueType> JsonSerializer<ExperimentResult<ValueType>> getSerializer() {
        return new JsonSerializer<ExperimentResult<ValueType>>() {
            @Override
            public JsonElement serialize(ExperimentResult<ValueType> src, Type typeOfSrc, JsonSerializationContext context) {
                return ExperimentResult.getJson(src);
            }
        };
    }

    // endregion Serialization
}
