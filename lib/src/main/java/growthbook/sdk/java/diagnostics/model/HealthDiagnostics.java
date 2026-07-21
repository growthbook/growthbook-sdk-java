package growthbook.sdk.java.diagnostics.model;

import java.util.List;

import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsHealthState;
import lombok.Builder;
import lombok.Value;

/**
 * High-level health summary derived from all diagnostics sections.
 */
@Value
@Builder
public class HealthDiagnostics {
    /**
     * High-level SDK health state.
     */
    DiagnosticsHealthState state;

    /**
     * Actionable issues detected in the current snapshot.
     */
    List<DiagnosticsIssue> issues;
}
