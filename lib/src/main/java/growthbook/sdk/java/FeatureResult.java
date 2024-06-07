package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Results for a {@link FeatureEvaluator#evaluateFeature(String, GBContext, Class, JsonObject)}
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

    /**
     * The assigned value of the feature
     */
    @Nullable
    @SerializedName("value")
    Object value;

    /**
     * One of "unknownFeature", "defaultValue", "force", "experiment",
     * "cyclicPrerequisite" or "prerequisite"
     */
    @Nullable
    FeatureResultSource source;

    /**
     * When source is "experiment", this will be the Experiment object used
     */
    @Nullable
    Experiment<ValueType> experiment;

    /**
     * When source is "experiment", this will be an ExperimentResult object
     */
    @Nullable
    ExperimentResult<ValueType> experimentResult;

    /**
     * Unique identifier of rule
     */
    @Nullable
    String ruleId;

    /**
     * Get a Gson JsonElement of the {@link FeatureResult}
     *
     * @return a Gson JsonElement
     */
    public String toJson() {
        return FeatureResult.getJson(this).toString();
    }

    /**
     * Evaluates to true when the feature is on
     *
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

        if (value instanceof Double) {
            return (Double) value != 0;
        }

        if (value instanceof Collection<?>) {
            return !((Collection<?>) value).isEmpty();
        }

        return false;
    }

    /**
     * Evaluates to true when the feature is off
     *
     * @return Boolean
     */
    public Boolean isOff() {
        return !isOn();
    }

    /**
     * Get a Gson JsonElement of the {@link FeatureResult}
     *
     * @param object      {@link FeatureResult}
     * @param <ValueType> value type for the feature
     * @return a Gson JsonElement
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
     *
     * @param <ValueType> {@link FeatureResult}
     * @return Gson serializer
     */
    public static <ValueType> JsonSerializer<FeatureResult<ValueType>> getSerializer() {
        return (src, typeOfSrc, context) -> FeatureResult.getJson(src);
    }
}
