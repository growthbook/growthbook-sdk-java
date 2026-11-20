package growthbook.sdk.java.evaluators.internal.condition;

/**
 * Thrown when evaluation reaches a part of a condition that is not well-formed, such as an
 * {@code $or} whose operand is not an array. The whole condition then fails.
 */
public final class MalformedConditionException extends RuntimeException {

    MalformedConditionException(String message) {
        super(message);
    }
}
