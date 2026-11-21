package growthbook.sdk.java.evaluators;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.AttrPath;
import growthbook.sdk.java.evaluators.internal.condition.CondParser;
import growthbook.sdk.java.evaluators.internal.condition.Conditions;

import javax.annotation.Nullable;

/**
 * <b>INTERNAL</b>: Implementation of condition evaluation.
 *
 * <p>Public only so the evaluators package can share it; it is not part of the supported API.
 * Even so, {@link #getPath} and {@link #isOperatorObject} keep the exact return types published
 * in 0.11.0: a return type is part of the JVM method descriptor, so narrowing one is a
 * {@link NoSuchMethodError} for anything linked against that release, which no compiler warns
 * about.
 *
 * <p>Stateless and safe for concurrent use; delegates to {@link Conditions}.
 */
public class ConditionEvaluator implements IConditionEvaluator {

    /**
     * Evaluate a condition for a set of user attributes based on the provided condition.
     * The condition syntax closely resembles MongoDB's syntax.
     * This is defined in the Feature's targeting conditions' Advanced settings.
     *
     * <p>A condition that depends on an operator or saved group type this SDK doesn't support
     * fails, including under {@code $not} and {@code $nor}. A malformed condition fails as a whole.
     *
     * @param attributes    A JsonObject of the user attributes to evaluate
     * @param conditionJson A JsonObject of the condition
     * @return Whether the condition should be true for the user
     */
    @Override
    public Boolean evaluateCondition(JsonObject attributes, JsonObject conditionJson, @Nullable JsonObject savedGroups) {
        return Conditions.matches(attributes, conditionJson, savedGroups);
    }

    /**
     * Returns a boxed {@code Boolean} to keep the signature published in 0.11.0: a return type is
     * part of the JVM method descriptor, so narrowing it to {@code boolean} is a
     * {@link NoSuchMethodError} for callers compiled against that release. The value is never null.
     *
     * @param object The object to evaluate
     * @return true if the object is empty or every key is an operator (starts with {@code $})
     */
    public Boolean isOperatorObject(JsonElement object) {
        return CondParser.isOperatorObject(object);
    }

    /**
     * Resolves a dot-separated path against the attributes.
     *
     * <p>Declared as {@code Object} to keep the signature published in 0.11.0, for the same
     * binary-compatibility reason as {@link #isOperatorObject}. Every value it can return is a
     * {@link JsonElement} or {@code null}.
     *
     * @param attributes User attributes
     * @param path       String path, e.g. {@code path.to.something}
     * @return the value at that path, or {@code null} if the path doesn't exist
     */
    @Nullable
    public Object getPath(JsonElement attributes, String path) {
        return AttrPath.parse(path).resolve(attributes);
    }
}
