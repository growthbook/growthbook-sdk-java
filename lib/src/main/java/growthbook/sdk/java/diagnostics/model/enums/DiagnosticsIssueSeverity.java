package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Diagnostics issue severity levels.
 */
public enum DiagnosticsIssueSeverity {
    /**
     * Informational state that is usually not actionable.
     */
    INFO,

    /**
     * Degraded behavior that should be investigated.
     */
    WARNING,

    /**
     * Error state that can affect evaluations or refresh behavior.
     */
    ERROR
}
