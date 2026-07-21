package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Source used to report feature diagnostics.
 */
public enum FeatureDataSource {
    /**
     * No feature source is currently available.
     */
    NONE,

    /**
     * Feature data comes from the local feature repository.
     */
    LOCAL_REPOSITORY,

    /**
     * Feature data represents the remote-eval fallback context.
     */
    REMOTE_EVAL_FALLBACK
}
