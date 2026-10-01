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
 *
 * <p>Widened from package-private to public so {@link Condition}'s strategies, which live in this
 * package but are referenced from the enum's public constants, can accept it. Despite the modifier
 * this is not supported API.</p>
 */
public interface IConditionEvaluator {
    Boolean evaluateCondition(JsonObject attributesJson, JsonObject conditionJson, @Nullable JsonObject savedGroups);
}
