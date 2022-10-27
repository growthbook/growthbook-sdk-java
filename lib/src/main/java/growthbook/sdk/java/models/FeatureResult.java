package growthbook.sdk.java.models;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

@Data
@Builder
@AllArgsConstructor
public class FeatureResult<ValueType> {

    @Nullable
    @SerializedName("value")
    Object value;

    /**
     * One of "unknownFeature", "defaultValue", "force", or "experiment"
     */
    @Nullable
    FeatureResultSource source;

    /**
     * When source is "experiment", this will be an Experiment object
     */
    @Nullable
    Experiment<ValueType> experiment;

    // TODO: ExperimentResult experimentResult
    // When source is "experiment", this will be an ExperimentResult object
    @Nullable
    ExperimentResult<ValueType> experimentResult;

    @Nullable
    String ruleId;

    public Boolean isOn() {
        if (value == null) return false;

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return !((String) value).isEmpty();
        }

        if (value instanceof Integer) {
            return (Integer) value != 0;
        }

        if (value instanceof Float) {
            return (Float) value != 0.0f;
        }

        return false;
    }

    public Boolean isOff() {
        return !isOn();
    }

    public static <ValueType> JsonElement getJson(FeatureResult<ValueType> object) {
        JsonObject jsonObject = new JsonObject();
        JsonPrimitive isOn = new JsonPrimitive(object.isOn());
        JsonPrimitive isOff = new JsonPrimitive(object.isOff());
        jsonObject.add("on", isOn);
        jsonObject.add("off", isOff);

        FeatureResultSource source = object.getSource();
        if (source != null) {
            JsonPrimitive jsonSource = new JsonPrimitive(source.toString());
            jsonObject.add("source", jsonSource);
        }

        return jsonObject;
    }

    public static <ValueType> JsonSerializer<FeatureResult<ValueType>> getSerializer() {
        return new JsonSerializer<FeatureResult<ValueType>>() {
            @Override
            public JsonElement serialize(FeatureResult<ValueType> src, Type typeOfSrc, JsonSerializationContext context) {
                return FeatureResult.getJson(src);
            }
        };
    }
}
