package growthbook.sdk.java;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

/**
 * Results for a {@link FeatureEvaluator#evaluateFeature(String, GBContext, Class)}
 *
 * <ul>
 * <li>value (any) - The assigned value of the feature</li>
 * <li>on (boolean) - The assigned value cast to a boolean</li>
 * <li>off (boolean) - The assigned value cast to a boolean and then negated</li>
 * <li>source (enum) - One of "unknownFeature", "defaultValue", "force", or "experiment"</li>
 * <li>experiment (Experiment or null) - When source is "experiment", this will be an Experiment object</li>
 * <li>experimentResult (ExperimentResult or null) - When source is "experiment", this will be an ExperimentResult object</li>
 * </ul>
 *
 * @param <ValueType> value type for the feature
 */
@Data
@Builder
@AllArgsConstructor
public class FeatureResult<ValueType> {

    @Nullable
    @SerializedName("value")
    Object value;

    @Nullable
    FeatureResultSource source;

    @Nullable
    Experiment<ValueType> experiment;

    @Nullable
    ExperimentResult<ValueType> experimentResult;

    @Nullable
    String ruleId;

    /**
     * Get a Gson JsonElement of the {@link FeatureResult}
     * @return a Gson JsonElement
     */
    public String toJson() {
        return FeatureResult.getJson(this).toString();
    }

    /**
     * Evaluates to true when the feature is on
     * @return Boolean
     */
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

    /**
     * Evaluates to true when the feature is off
     * @return Boolean
     */
    public Boolean isOff() {
        return !isOn();
    }

    /**
     * Get a Gson JsonElement of the {@link FeatureResult}
     * @param object {@link FeatureResult}
     * @return a Gson JsonElement
     * @param <ValueType> value type for the feature
     */
    public static <ValueType> JsonElement getJson(FeatureResult<ValueType> object) {
        JsonObject jsonObject = new JsonObject();
        JsonPrimitive isOn = new JsonPrimitive(object.isOn());
        JsonPrimitive isOff = new JsonPrimitive(object.isOff());
        jsonObject.add("on", isOn);
        jsonObject.add("off", isOff);

        Object value = object.getValue();
        JsonElement valueElement = GrowthBookJsonUtils.getInstance().gson.toJsonTree(value);
        jsonObject.add("value", valueElement);

        Experiment<ValueType> experiment = object.getExperiment();
        JsonElement experimentElement = GrowthBookJsonUtils.getInstance().gson.toJsonTree(experiment);
        jsonObject.add("experiment", experimentElement);

        ExperimentResult<ValueType> experimentResult = object.getExperimentResult();
        JsonElement experimentResultElement = GrowthBookJsonUtils.getInstance().gson.toJsonTree(experimentResult);
        jsonObject.add("experimentResult", experimentResultElement);

        FeatureResultSource source = object.getSource();
        if (source != null) {
            JsonPrimitive jsonSource = new JsonPrimitive(source.toString());
            jsonObject.add("source", jsonSource);
        }

        return jsonObject;
    }

    /**
     * a Gson serializer for {@link FeatureResult}
     * @return Gson serializer
     * @param <ValueType> {@link FeatureResult}
     */
    public static <ValueType> JsonSerializer<FeatureResult<ValueType>> getSerializer() {
        return new JsonSerializer<FeatureResult<ValueType>>() {
            @Override
            public JsonElement serialize(FeatureResult<ValueType> src, Type typeOfSrc, JsonSerializationContext context) {
                return FeatureResult.getJson(src);
            }
        };
    }
}
