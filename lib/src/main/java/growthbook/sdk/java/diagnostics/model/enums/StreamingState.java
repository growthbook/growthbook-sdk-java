package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Derived streaming connection state.
 */
public enum StreamingState {
    /**
     * Streaming is not configured.
     */
    DISABLED,

    /**
     * Client resources have been shut down.
     */
    SHUTDOWN,

    /**
     * Streaming is configured but not yet fully connected or classified.
     */
    INITIALIZING,

    /**
     * Streaming is configured, supported, and connected.
     */
    CONNECTED,

    /**
     * Streaming was connected/supported and is currently reconnecting.
     */
    INTERRUPTED,

    /**
     * Streaming is configured but the server did not allow SSE.
     */
    UNSUPPORTED
}
