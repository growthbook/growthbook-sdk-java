package growthbook.sdk.java.diagnostics.model;

import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsIssueCode;
import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsIssueSeverity;
import lombok.Builder;
import lombok.Value;

/**
 * Actionable diagnostics issue derived from the raw SDK state snapshot.
 */
@Value
@Builder
public class DiagnosticsIssue {
    /**
     * Stable machine-readable issue code.
     */
    DiagnosticsIssueCode code;

    /**
     * Issue severity for support and alerting flows.
     */
    DiagnosticsIssueSeverity severity;

    /**
     * Human-readable issue summary.
     */
    String message;

    /**
     * Epoch-millis timestamp when the diagnostics snapshot was generated.
     */
    long timestampMillis;
}
