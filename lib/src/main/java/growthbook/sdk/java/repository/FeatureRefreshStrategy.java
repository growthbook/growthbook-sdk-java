package growthbook.sdk.java.repository;

/**
 * Enum that used in strategy for building url
 */
public enum FeatureRefreshStrategy {
    STALE_WHILE_REVALIDATE,
    SERVER_SENT_EVENTS,
    REMOTE_EVAL_STRATEGY
}
