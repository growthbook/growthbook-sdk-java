package growthbook.sdk.java.model;

/**
 * Identifies what triggered a feature refresh.
 *
 * <p>The source reflects the trigger only. Whether the delivered data came from cache is reported
 * separately by {@link FeatureRefreshEvent#isLoadedFromCache()}.
 */
public enum FeatureRefreshSource {
    INITIALIZATION,
    MANUAL,
    POLLING,
    SSE,
    REMOTE_EVALUATION
}
