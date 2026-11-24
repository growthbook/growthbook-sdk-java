package growthbook.sdk.java.model;

/**
 * Identifies what triggered a feature refresh.
 *
 * <p>The source reflects the trigger only. Whether the delivered data came from cache is reported
 * separately by {@link FeatureRefreshEvent#isLoadedFromCache()}.
 */
public enum FeatureRefreshSource {
    /**
     * The initial feature load triggered by {@code initialize()}, including any cache fallback that
     * runs when the first network fetch fails.
     */
    INITIALIZATION,
    /**
     * Seeding from an inline bootstrap payload supplied at construction time. Delivered before any
     * network refresh; events carrying this source are not a successful network fetch and are always
     * reported with {@link FeatureRefreshEvent#isLoadedFromCache()} set to {@code true}.
     */
    INITIAL_PAYLOAD,
    /**
     * A refresh explicitly requested by the caller, e.g. {@code refreshFeatures()} / {@code fetchFeatures()}.
     */
    MANUAL,
    /**
     * A scheduled background refresh under the {@link growthbook.sdk.java.repository.FeatureRefreshStrategy#STALE_WHILE_REVALIDATE}
     * strategy.
     */
    POLLING,
    /**
     * A refresh pushed by the server over the Server-Sent Events stream under the
     * {@link growthbook.sdk.java.repository.FeatureRefreshStrategy#SERVER_SENT_EVENTS} strategy.
     */
    SSE,
    /**
     * A features fetch performed for remote evaluation under the
     * {@link growthbook.sdk.java.repository.FeatureRefreshStrategy#REMOTE_EVAL_STRATEGY} strategy.
     */
    REMOTE_EVALUATION
}
