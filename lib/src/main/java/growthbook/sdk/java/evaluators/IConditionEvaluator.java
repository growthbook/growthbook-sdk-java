package growthbook.sdk.java.evaluators;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

/**
 * <b>INTERNAL</b>: Recursion entry point for condition evaluation.
 *
 * <p>Logical-operator strategies receive an instance of this interface and call back into
 * {@link #evaluateCondition} for nested sub-conditions, so the recursion funnels through a
 * single point (the Interpreter pattern) instead of each strategy depending on the concrete
 * evaluator.</p>
 */
public interface IConditionEvaluator {
    Boolean evaluateCondition(JsonObject attributesJson, JsonObject conditionJson, @Nullable JsonObject savedGroups);
}
