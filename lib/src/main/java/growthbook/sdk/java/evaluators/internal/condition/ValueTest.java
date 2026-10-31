package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;

import javax.annotation.Nullable;

/**
 * A parsed condition value — a literal to compare with, or an object of operators — tested
 * against one attribute value. Immutable and safe for concurrent use.
 */
@FunctionalInterface
interface ValueTest {

    /**
     * @param actual the attribute value; a missing attribute is {@link com.google.gson.JsonNull}
     * @param scope  the saved groups to resolve {@code $inGroup} against
     * @return whether the value matches, or {@code null} when that is unknown
     * @throws MalformedConditionException when evaluation reaches a malformed operand
     */
    @Nullable
    Boolean test(@Nullable JsonElement actual, CondScope scope);
}
