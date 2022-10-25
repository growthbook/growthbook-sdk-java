package growthbook.sdk.java.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.services.GrowthBookJsonUtils;

import javax.annotation.Nullable;

public class Feature {

    // TODO: Rules?

    private final String rawValue;
    private final DataType dataType;

    private final JsonObject featureJson;
    private final JsonArray rules;

    private final String defaultValue;

    public Feature(String rawValue) {
        this.rawValue = rawValue;
        this.dataType = Feature.getValueDataType(rawValue);
        this.featureJson = Feature.getFeatureJsonFromRawValue(rawValue);
        this.defaultValue = featureJson.get("defaultValue").toString();
        this.rules = Feature.getRulesFromFeatureJson(this.featureJson);

        // TODO: Transform other things??
    }

    public String getDefaultValue() {
        return this.defaultValue;
    }

    public String getRulesJsonArray() {
        return this.rules.toString();
    }

    private static JsonObject getFeatureJsonFromRawValue(String rawValue) {
        try {
            return GrowthBookJsonUtils.getInstance().gson.fromJson(rawValue, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new JsonObject();
        }
    }

    private static JsonArray getRulesFromFeatureJson(JsonObject json) {
        try {
            return json.get("rules").getAsJsonArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new JsonArray();
        }
    }

    public String getRawValue() {
        return this.rawValue;
    }

    public DataType getDataType() {
        return this.dataType;
    }

    @Nullable
    private static DataType getValueDataType(String rawValue) {
        try {
            JsonElement jsonElement = GrowthBookJsonUtils.getInstance()
                    .gson.fromJson(rawValue, JsonElement.class);
            return GrowthBookJsonUtils.getElementType(jsonElement);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
