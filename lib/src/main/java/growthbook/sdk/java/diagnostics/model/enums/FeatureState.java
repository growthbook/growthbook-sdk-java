package growthbook.sdk.java.diagnostics.model.enums;

/**
 * Derived feature data state.
 */
public enum FeatureState {
    /**
     * No feature data has been loaded.
     */
    NOT_LOADED,

    /**
     * Feature data has been loaded, but it contains no feature definitions.
     */
    LOADED_EMPTY,

    /**
     * Feature data has been loaded and contains feature definitions.
     */
    LOADED
}
