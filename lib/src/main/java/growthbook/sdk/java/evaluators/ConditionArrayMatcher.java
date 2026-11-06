package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;

/**
 * <b>INTERNAL</b>: Shared array-of-conditions matching used by the {@code $or}/{@code $nor}/{@code $and}
 * logical operators. Each nested condition is delegated back to the {@link ConditionEvaluator}.
 *
 * <p>Results are {@code true}, {@code false}, or {@code null} when they depend on something this
 * SDK can't evaluate.</p>
 */
@UtilityClass
final class ConditionArrayMatcher {

    /**
     * @return true if any condition matches (an empty array matches, mirroring {@code $or}
     * semantics); otherwise null if any condition is unknown, else false
     */
    @Nullable
    static Boolean anyMatches(@Nullable JsonElement attributes, JsonArray conditions, ConditionEvaluator.Scope scope, ConditionEvaluator evaluator) {
        if (conditions.isEmpty()) {
            return true;
        }
        Boolean result = false;
        for (JsonElement condition : conditions) {
            Boolean matched = evaluator.evalCondition(attributes, condition.getAsJsonObject(), scope);
            if (Boolean.TRUE.equals(matched)) {
                return true;
            }
            if (matched == null) {
                result = null;
            }
        }
        return result;
    }

    /**
     * @return false if any condition fails; otherwise null if any condition is unknown, else true
     * (an empty array matches, mirroring {@code $and} semantics)
     */
    @Nullable
    static Boolean allMatch(@Nullable JsonElement attributes, JsonArray conditions, ConditionEvaluator.Scope scope, ConditionEvaluator evaluator) {
        Boolean result = true;
        for (JsonElement condition : conditions) {
            Boolean matched = evaluator.evalCondition(attributes, condition.getAsJsonObject(), scope);
            if (Boolean.FALSE.equals(matched)) {
                return false;
            }
            if (matched == null) {
                result = null;
            }
        }
        return result;
    }
}
