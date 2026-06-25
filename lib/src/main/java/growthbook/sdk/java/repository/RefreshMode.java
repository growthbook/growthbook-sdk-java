package growthbook.sdk.java.repository;

/**
 * Controls whether a feature refresh may use cache freshness checks.
 */
public enum RefreshMode {
    /**
     * Honor the configured background fetch interval.
     */
    DEFAULT,

    /**
     * Always perform a network request and bypass cache freshness checks.
     */
    FORCE
}
