package growthbook.sdk.java.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.services.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * The feature with a generic value type.
 * <ul>
 * <li>defaultValue (any) - The default value (should use null if not specified)</li>
 * <li>rules (FeatureRule[]) - Array of FeatureRule objects that determine when and how the defaultValue gets overridden</li>
 * </ul>
 * @param <ValueType> value type for the feature
 */
public class Feature<ValueType> {

    @Nullable
    private final ArrayList<FeatureRule<ValueType>> rules;

    private final Object defaultValue;

    /**
     * Construct a feature with the JSON string
     * @param rawValue JSON string of the feature
     */
    public Feature(String rawValue) {
        JsonObject featureJson = Feature.getFeatureJsonFromRawValue(rawValue);
        this.defaultValue = GrowthBookJsonUtils.unwrap(featureJson.get("defaultValue"));
        this.rules = Feature.getRulesFromFeatureJson(featureJson);
    }

    /**
     * The default value for a feature evaluation
     * @return value of the feature
     */
    public Object getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * Returns the rules for evaluating the feature
     * @return rules list
     */
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
}
