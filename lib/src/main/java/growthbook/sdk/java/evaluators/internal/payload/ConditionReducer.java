package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.CondPruner;
import growthbook.sdk.java.evaluators.internal.condition.JsonValues;

/**
 * Reduces the conditions of rules, auto experiments and bandit contexts against the known
 * attributes.
 */
final class ConditionReducer {

    private final JsonObject attributes;
    private final JsonObject savedGroups;

    ConditionReducer(JsonObject attributes, JsonObject savedGroups) {
        this.attributes = attributes;
        this.savedGroups = savedGroups;
    }

    /**
     * @return a boolean once decided, otherwise the condition minus every decided branch
     */
    JsonElement prune(JsonObject condition) {
        return CondPruner.pruneCondition(attributes, condition, savedGroups);
    }

    /**
     * Reduces a rule's or an auto experiment's condition: one that always passes is removed, and
     * one that never passes becomes {@code {"$not": {}}}.
     *
     * @param item   the rule or experiment
     * @param isRule whether it is a feature rule
     * @return a copy with the condition reduced; anything but an object is returned as is
     */
    JsonElement reduce(JsonElement item, boolean isRule) {
        if (!item.isJsonObject()) {
            return item;
        }
        JsonObject reduced = PayloadJson.shallowCopy(item.getAsJsonObject());
        JsonElement condition = reduced.get(PayloadJson.CONDITION);
        if (condition == null || !condition.isJsonObject()) {
            return reduced;
        }
        JsonElement pruned = prune(condition.getAsJsonObject());
        Boolean decided = JsonValues.asBoolean(pruned);
        if (decided == null) {
            reduced.add(PayloadJson.CONDITION, pruned);
        } else if (decided) {
            reduced.remove(PayloadJson.CONDITION);
        } else {
            markNeverMatching(reduced, isRule);
        }
        return reduced;
    }

    /**
     * Keeps a condition that never passes rather than dropping the item, since sticky buckets and
     * forced variations skip an experiment's condition, and a rule's prerequisites are checked
     * before it. A force rule that never passes never returns its value; an auto experiment's
     * forced variation still applies to a sticky bucketed user.
     */
    private static void markNeverMatching(JsonObject reduced, boolean isRule) {
        JsonObject never = new JsonObject();
        never.add("$not", new JsonObject());
        reduced.add(PayloadJson.CONDITION, never);
        if (isRule && reduced.has("force")) {
            reduced.add("force", JsonNull.INSTANCE);
        }
    }
}
