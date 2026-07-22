package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Stable machine-readable diagnostics issue codes.
 */
public enum DiagnosticsIssueCode {
    /**
     * The client has not completed initialization.
     */
    CLIENT_NOT_INITIALIZED,

    /**
     * The client has been shut down.
     */
    CLIENT_SHUTDOWN,

    /**
     * Local feature data is required but not currently loaded.
     */
    FEATURES_NOT_LOADED,

    /**
     * Feature data is loaded but contains no feature definitions.
     */
    FEATURES_EMPTY,

    /**
     * The most recent feature refresh or initialization attempt failed.
     */
    REFRESH_FAILED,

    /**
     * Cache persistence is enabled but cache state cannot be inspected.
     */
    CACHE_STATE_UNKNOWN,

    /**
     * Streaming refresh is configured but not supported by the server response.
     */
    STREAMING_UNSUPPORTED,

    /**
     * Streaming refresh is configured and supported, but is currently reconnecting.
     */
    STREAMING_INTERRUPTED,

    /**
     * Remote evaluation is configured but not ready.
     */
    REMOTE_EVAL_NOT_READY,

    /**
     * Feature data is loaded but past its cache expiry.
     */
    FEATURES_STALE,

    /**
     * SDK evaluations are globally disabled by configuration.
     */
    SDK_DISABLED,

    /**
     * QA mode is enabled by configuration.
     */
    QA_MODE_ENABLED
}
