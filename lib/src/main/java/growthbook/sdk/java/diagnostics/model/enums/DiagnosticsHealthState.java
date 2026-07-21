package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Derived high-level SDK health state.
 */
public enum DiagnosticsHealthState {
    /**
     * The client is initialized and no actionable diagnostics issues were detected.
     */
    READY,

    /**
     * The client has not finished initialization yet.
     */
    NOT_READY,

    /**
     * The client can still evaluate, but at least one supporting subsystem is degraded.
     */
    DEGRADED,

    /**
     * The client is in an error state that can affect evaluations.
     */
    ERROR,

    /**
     * The client has been shut down.
     */
    SHUTDOWN
}
