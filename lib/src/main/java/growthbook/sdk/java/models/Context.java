package growthbook.sdk.java.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class Context {
    /**
     * Switch to globally disable all experiments
     */
    @Builder.Default
    Boolean enabled = true;

    /**
     * The URL of the current page
     */
    String url;

    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    @Builder.Default
    Boolean isQaMode = false;

    // TODO: TrackingCallback
//    /**
//     * A function that takes `experiment` and `result` as arguments.
//     */
    // TODO: Attributes
//    /**
//     * Map of user attributes that are used to assign variations
//     */

    // TODO: Features
//    /**
//     * Feature definitions (usually pulled from an API or cache)
//     */

    // TODO: ForcedVariations
//    /**
//     * Force specific experiments to always assign a specific variation (used for QA)
//     */
}
