package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;

/**
 * <b>INTERNAL</b>: Shared array-of-conditions matching used by the {@code $or}/{@code $nor}/{@code $and}
 * logical operators. Each nested condition is delegated back to the {@link IConditionEvaluator}.
 */
@UtilityClass
final class ConditionArrayMatcher {

    /**
     * @return true if any condition matches (an empty array matches, mirroring {@code $or} semantics)
     */
    static boolean anyMatches(JsonObject attributes, JsonArray conditions, @Nullable JsonObject savedGroups, IConditionEvaluator evaluator) {
        if (conditions.isEmpty()) {
            return true;
        }
        for (JsonElement condition : conditions) {
            if (Boolean.TRUE.equals(evaluator.evaluateCondition(safe(attributes), condition.getAsJsonObject(), savedGroups))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if every condition matches (an empty array matches, mirroring {@code $and} semantics)
     */
    static boolean allMatch(JsonObject attributes, JsonArray conditions, @Nullable JsonObject savedGroups, IConditionEvaluator evaluator) {
        for (JsonElement condition : conditions) {
            if (!Boolean.TRUE.equals(evaluator.evaluateCondition(safe(attributes), condition.getAsJsonObject(), savedGroups))) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject safe(@Nullable JsonObject attributes) {
        return attributes == null ? new JsonObject() : attributes;
    }
}
