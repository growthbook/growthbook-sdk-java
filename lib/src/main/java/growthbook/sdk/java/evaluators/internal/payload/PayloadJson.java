package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Payload keys and the JSON helpers payload reduction shares. Inputs are never modified.
 */
final class PayloadJson {

    static final String FEATURES = "features";
    static final String EXPERIMENTS = "experiments";
    static final String SAVED_GROUPS = "savedGroups";
    static final String CONTEXTUAL_BANDITS = "contextualBandits";
    static final String RULES = "rules";
    static final String CONDITION = "condition";
    static final String METADATA = "metadata";
    static final String PARENT_CONDITIONS = "parentConditions";

    private PayloadJson() {
    }

    static JsonObject objectOrEmpty(@Nullable JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    /**
     * @return the array under {@code key} of an object, or {@code null} when there is none
     */
    @Nullable
    static JsonArray arrayField(@Nullable JsonElement item, String key) {
        JsonElement value = item != null && item.isJsonObject() ? item.getAsJsonObject().get(key) : null;
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    static JsonObject metadataOf(@Nullable JsonElement item) {
        JsonElement metadata = item != null && item.isJsonObject() ? item.getAsJsonObject().get(METADATA) : null;
        return objectOrEmpty(metadata);
    }

    static JsonObject shallowCopy(JsonObject source) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            copy.add(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}
