package growthbook.sdk.java.evaluators;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Top-level logical operators that may appear as keys in a condition object.
 *
 * <p>Each constant carries the strategy that combines its operand against the user attributes.
 * Conditions are recursive, so a strategy receives the {@link IConditionEvaluator} and calls back
 * into it for nested sub-conditions (the Interpreter pattern). Adding a logical operator is a
 * single entry here.</p>
 */
@Getter
public enum Condition {

    OR("$or", (attributes, value, savedGroups, evaluator) ->
            ConditionArrayMatcher.anyMatches(attributes, value.getAsJsonArray(), savedGroups, evaluator)),
    NOR("$nor", (attributes, value, savedGroups, evaluator) ->
            !ConditionArrayMatcher.anyMatches(attributes, value.getAsJsonArray(), savedGroups, evaluator)),
    AND("$and", (attributes, value, savedGroups, evaluator) ->
            ConditionArrayMatcher.allMatch(attributes, value.getAsJsonArray(), savedGroups, evaluator)),
    NOT("$not", (attributes, value, savedGroups, evaluator) ->
            !evaluator.evaluateCondition(attributes, value.getAsJsonObject(), savedGroups));

    /**
     * Combines a logical operator's operand against the user attributes, recursing through the
     * evaluator for nested sub-conditions.
     */
    @FunctionalInterface
    interface Strategy {
        boolean apply(JsonObject attributes, JsonElement value, @Nullable JsonObject savedGroups, IConditionEvaluator evaluator);
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

    boolean apply(JsonObject attributes, JsonElement value, @Nullable JsonObject savedGroups, IConditionEvaluator evaluator) {
        return strategy.apply(attributes, value, savedGroups, evaluator);
    }
}
