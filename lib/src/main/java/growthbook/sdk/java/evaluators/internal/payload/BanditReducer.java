package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.JsonValues;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the contextual bandits that kept rules and experiments use. A context whose condition
 * never passes can't be the first that does, so it goes.
 */
final class BanditReducer {

    private final JsonObject allBandits;
    private final ConditionReducer conditions;

    BanditReducer(JsonObject allBandits, ConditionReducer conditions) {
        this.allBandits = allBandits;
        this.conditions = conditions;
    }

    JsonObject reduce(JsonObject features, JsonArray experiments) {
        JsonObject bandits = new JsonObject();
        for (JsonElement item : rulesAndExperiments(features, experiments)) {
            String ref = item.isJsonObject() ? JsonValues.stringField(item.getAsJsonObject(), "contextualBanditRef") : null;
            if (ref != null && !ref.isEmpty() && !bandits.has(ref) && allBandits.has(ref)) {
                bandits.add(ref, reduceBandit(allBandits.get(ref)));
            }
        }
        return bandits;
    }

    private static List<JsonElement> rulesAndExperiments(JsonObject features, JsonArray experiments) {
        List<JsonElement> items = new ArrayList<>();
        for (String id : features.keySet()) {
            JsonArray rules = PayloadJson.arrayField(features.get(id), PayloadJson.RULES);
            if (rules != null) {
                rules.forEach(items::add);
            }
        }
        experiments.forEach(items::add);
        return items;
    }

    /**
     * A definition that isn't an object stays as is, since evaluation tolerates it.
     */
    private JsonElement reduceBandit(JsonElement definition) {
        if (!definition.isJsonObject()) {
            return definition;
        }
        JsonObject bandit = PayloadJson.shallowCopy(definition.getAsJsonObject());
        JsonArray contexts = new JsonArray();
        JsonArray allContexts = PayloadJson.arrayField(bandit, "contexts");
        if (allContexts != null) {
            for (JsonElement context : allContexts) {
                JsonElement reducedContext = reduceContext(context);
                if (reducedContext != null) {
                    contexts.add(reducedContext);
                }
            }
        }
        bandit.add("contexts", contexts);
        return bandit;
    }

    /**
     * @return the context with its condition reduced, or {@code null} when it never passes. A
     * context that isn't an object is kept as is rather than failing the whole reduction.
     */
    @Nullable
    private JsonElement reduceContext(JsonElement context) {
        if (!context.isJsonObject()) {
            return context;
        }
        JsonObject reduced = PayloadJson.shallowCopy(context.getAsJsonObject());
        JsonElement condition = reduced.get(PayloadJson.CONDITION);
        JsonElement pruned = conditions.prune(condition != null && condition.isJsonObject() ? condition.getAsJsonObject() : new JsonObject());
        Boolean decided = JsonValues.asBoolean(pruned);
        if (Boolean.FALSE.equals(decided)) {
            return null;
        }
        reduced.add(PayloadJson.CONDITION, decided != null ? new JsonObject() : pruned);
        return reduced;
    }
}
