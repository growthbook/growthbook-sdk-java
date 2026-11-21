package growthbook.sdk.java.evaluators;

import com.google.gson.JsonElement;
import growthbook.sdk.java.evaluators.internal.condition.CondParser;
import growthbook.sdk.java.evaluators.internal.condition.CondScope;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Top-level logical operators that may appear as keys in a condition object.
 *
 * <p>Strategies return {@code true}, {@code false}, or {@code null} when the result depends on an
 * operator or saved group type this SDK doesn't support. {@code null} stays {@code null} under
 * {@code $not} and {@code $nor}, so negating an unsupported condition never makes it pass.</p>
 *
 * <p>Evaluation itself parses conditions with {@link CondParser}; each strategy delegates there.</p>
 *
 * @deprecated nothing in the SDK uses this enum any more; evaluate conditions with
 * {@link ConditionEvaluator#evaluateCondition}. It will be removed in a future release.
 */
@Deprecated
@Getter
public enum Condition {

    OR("$or"),
    NOR("$nor"),
    AND("$and"),
    NOT("$not");

    /**
     * Combines a logical operator's operand against the user attributes. Returns {@code null}
     * when the result is unknown.
     */
    @FunctionalInterface
    interface Strategy {
        @Nullable
        Boolean apply(@Nullable JsonElement attributes, JsonElement value, CondScope scope);
    }

    private static final Map<String, Condition> BY_VALUE = new HashMap<>();

    static {
        for (Condition condition : values()) {
            BY_VALUE.put(condition.conditionValue, condition);
        }
    }

    private final String conditionValue;
    private final Strategy strategy;

    Condition(String conditionValue) {
        this.conditionValue = conditionValue;
        this.strategy = (attributes, value, scope) -> CondParser.parseEntry(conditionValue, value).eval(attributes, scope);
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
    Boolean apply(@Nullable JsonElement attributes, JsonElement value, CondScope scope) {
        return strategy.apply(attributes, value, scope);
    }
}
