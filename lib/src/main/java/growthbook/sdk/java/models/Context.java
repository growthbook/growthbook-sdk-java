package growthbook.sdk.java.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Context object passed into the GrowthBook constructor.
 */
@Data
@Builder
@AllArgsConstructor
public class Context<TrackingCallbackResultType> {
    /**
     * Switch to globally disable all experiments
     */
    @Builder.Default
    Boolean enabled = true;

    /**
     * The URL of the current page
     */
    @Nullable
    String url;

    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    @Nullable
    @Builder.Default
    Boolean isQaMode = false;

    /**
     * A function that takes `experiment` and `result` as arguments.
     */
    TrackingCallback<TrackingCallbackResultType> trackingCallback;

    /**
     * Map of user attributes that are used to assign variations
     */
    @Nullable
    UserAttributes attributes;

    // TODO: Features
//    /**
//     * Feature definitions (usually pulled from an API or cache)
//     */

    // TODO: Would this be more user-friendly as its own type ForcedVariationsMap or Map<String, Integer> ?
    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    @Nullable
    Map<String, Integer> forcedVariationsMap;
//    ForcedVariationsMap forcedVariationsMap;
}
