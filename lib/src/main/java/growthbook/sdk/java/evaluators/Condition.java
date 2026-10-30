package growthbook.sdk.java.evaluators;

import com.google.gson.JsonElement;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Top-level logical operators that may appear as keys in a condition object.
 *
 * <p>Each constant carries the strategy that combines its operand against the user attributes.
 * Conditions are recursive, so a strategy receives the {@link ConditionEvaluator} and calls back
 * into it for nested sub-conditions (the Interpreter pattern). Adding a logical operator is a
 * single entry here.</p>
 *
 * <p>Strategies return {@code true}, {@code false}, or {@code null} when the result depends on an
 * operator or saved group type this SDK doesn't support. {@code null} stays {@code null} under
 * {@code $not} and {@code $nor}, so negating an unsupported condition never makes it pass.</p>
 */
@Getter
public enum Condition {

    OR("$or", (attributes, value, scope, evaluator) ->
            ConditionArrayMatcher.anyMatches(attributes, value.getAsJsonArray(), scope, evaluator)),
    NOR("$nor", (attributes, value, scope, evaluator) ->
            ConditionEvaluator.not(ConditionArrayMatcher.anyMatches(attributes, value.getAsJsonArray(), scope, evaluator))),
    AND("$and", (attributes, value, scope, evaluator) ->
            ConditionArrayMatcher.allMatch(attributes, value.getAsJsonArray(), scope, evaluator)),
    NOT("$not", (attributes, value, scope, evaluator) ->
            ConditionEvaluator.not(evaluator.evalCondition(attributes, value.getAsJsonObject(), scope)));

    /**
     * Combines a logical operator's operand against the user attributes, recursing through the
     * evaluator for nested sub-conditions. Returns {@code null} when the result is unknown.
     */
    @FunctionalInterface
    interface Strategy {
        @Nullable
        Boolean apply(@Nullable JsonElement attributes, JsonElement value, ConditionEvaluator.Scope scope, ConditionEvaluator evaluator);
    }

    private static final Map<String, Condition> BY_VALUE = new HashMap<>();

    static {
        for (Condition condition : values()) {
            BY_VALUE.put(condition.conditionValue, condition);
        }
    }

    private final String conditionValue;
    private final Strategy strategy;

    Condition(String conditionValue, Strategy strategy) {
        this.conditionValue = conditionValue;
        this.strategy = strategy;
    }

    /**
     * @param key a condition entry key (e.g. {@code "$or"})
     * @return the matching logical operator, or {@code null} if the key is an attribute path
     */
    @Nullable
    public static Condition fromValue(String key) {
        return BY_VALUE.get(key);
    }

    @Nullable
    Boolean apply(@Nullable JsonElement attributes, JsonElement value, ConditionEvaluator.Scope scope, ConditionEvaluator evaluator) {
        return strategy.apply(attributes, value, scope, evaluator);
    }
}
