package growthbook.sdk.java.sandbox;

/**
 * Cache behavior modes for the SDK.
 */
public enum CacheMode {
    /**
     * Select the best available mode automatically:
     * FILE if a writable directory is available, otherwise MEMORY.
     */
    AUTO,
    /**
     * No cache persistence at all. Repository still keeps runtime state in memory.
     */
    NONE,
    /**
     * In-process memory cache only (no filesystem persistence).
     */
    MEMORY,
    /**
     * Persist cache to filesystem directory.
     */
    FILE
}


