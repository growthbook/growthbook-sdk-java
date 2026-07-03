package growthbook.sdk.java.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when GrowthBook options fail start-up validation.
 *
 * <p>Extends {@link IllegalArgumentException} so existing callers that catch invalid-argument errors
 * keep working, while exposing the full list of problems via {@link #getViolations()} for callers
 * that want structured access (diagnostics, health checks, structured logging).
 */
@Getter
public class InvalidOptionsException extends IllegalArgumentException {

    /**
     * The individual, human-readable validation problems that were detected.
     */
    private final List<String> violations;

    public InvalidOptionsException(String message, List<String> violations) {
        super(message);
        this.violations = violations == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(violations));
    }

    /**
     * Creates an exception for a single validation problem, using the message as the only violation.
     *
     * @param message the problem description, used both as the exception message and the sole violation
     */
    public InvalidOptionsException(String message) {
        this(message, Collections.singletonList(message));
    }
}
