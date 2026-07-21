package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Derived feature cache state.
 */
public enum CacheState {
    /**
     * Cache persistence is disabled by configuration.
     */
    DISABLED,

    /**
     * Cache could be enabled, but the feature repository has not been initialized yet.
     */
    NOT_INITIALIZED,

    /**
     * Cache is enabled and exposes a last-updated timestamp.
     */
    AVAILABLE,

    /**
     * Cache is enabled, but its last-updated timestamp is unavailable.
     */
    UNKNOWN
}
