package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;

import javax.annotation.Nullable;

/**
 * A parsed condition. Immutable and safe for concurrent use.
 */
@FunctionalInterface
public interface Cond {

    /**
     * @param attributes the value the condition tests: the user attributes, or an array element
     *                   under {@code $elemMatch}
     * @param scope      the saved groups to resolve references against
     * @return whether the condition matches, or {@code null} when that is unknown
     * @throws MalformedConditionException when evaluation reaches a malformed part of the condition
     */
    @Nullable
    Boolean eval(@Nullable JsonElement attributes, CondScope scope);
}
