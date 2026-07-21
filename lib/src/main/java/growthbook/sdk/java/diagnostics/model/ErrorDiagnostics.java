package growthbook.sdk.java.diagnostics.model;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;

/**
 * Sanitized error metadata for diagnostics output.
 */
@Value
@Builder
public class ErrorDiagnostics {
    /**
     * Simple class name for the captured error.
     */
    String type;

    /**
     * SDK error code when the error exposes one.
     */
    @Nullable
    String code;

    /**
     * Sanitized error message.
     */
    String message;

    /**
     * Epoch-millis timestamp when the error was captured.
     */
    long timestampMillis;
}
