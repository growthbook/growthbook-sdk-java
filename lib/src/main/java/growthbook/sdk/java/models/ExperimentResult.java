package growthbook.sdk.java.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import growthbook.sdk.java.services.GrowthBookJsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

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
