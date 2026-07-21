package growthbook.sdk.java.diagnostics.model;

import growthbook.sdk.java.diagnostics.model.enums.FeatureDataSource;
import growthbook.sdk.java.diagnostics.model.enums.FeatureState;
import lombok.Builder;
import lombok.Value;

/**
 * Current feature state metadata.
 */
@Value
@Builder
public class FeatureDiagnostics {
    /**
     * Derived feature data state.
     */
    FeatureState state;

    /**
     * Source used to report feature diagnostics.
     */
    FeatureDataSource source;

    /**
     * Whether feature data is currently available for evaluations.
     */
    boolean available;

    /**
     * Number of active feature definitions in the current snapshot.
     */
    int activeFeatureCount;

    /**
     * Total number of feature definitions visible to diagnostics.
     */
    int totalFeatureCount;

    /**
     * Feature key metadata for the current snapshot.
     */
    FeatureKeyDiagnostics keys;
}
