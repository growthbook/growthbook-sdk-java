package growthbook.sdk.java.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.FeatureRule;
import growthbook.sdk.java.services.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class Feature<ValueType> {

    // TODO: Rules?

    private final String rawValue;
    private final DataType dataType;

    @Nullable
    private final ArrayList<FeatureRule<ValueType>> rules;

    private final Object defaultValue;

    public Feature(String rawValue) {
        this.rawValue = rawValue;
        this.dataType = Feature.getValueDataType(rawValue);

        JsonObject featureJson = Feature.getFeatureJsonFromRawValue(rawValue);
        this.defaultValue = GrowthBookJsonUtils.unwrap(featureJson.get("defaultValue"));
        this.rules = Feature.getRulesFromFeatureJson(featureJson);
    }

    public Object getDefaultValue() {
        return this.defaultValue;
    }

    @Nullable
    public ArrayList<FeatureRule<ValueType>> getRules() {
        return this.rules;
    }

    private static JsonObject getFeatureJsonFromRawValue(String rawValue) {
        try {
            return GrowthBookJsonUtils.getInstance().gson.fromJson(rawValue, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new JsonObject();
        }
    }

    private static <ValueType> ArrayList<FeatureRule<ValueType>> getRulesFromFeatureJson(JsonObject json) {
        try {
            // TODO: Verify that these end up correct
            Type featureRuleListType = new TypeToken<ArrayList<FeatureRule>>() {}.getType();
            JsonElement rulesJsonElement = json.get("rules");
            JsonArray rulesJson;
            if (rulesJsonElement != null) {
                rulesJson = rulesJsonElement.getAsJsonArray();
            } else {
                rulesJson = new JsonArray();
            }

            return GrowthBookJsonUtils.getInstance().gson.fromJson(rulesJson, featureRuleListType);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
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
