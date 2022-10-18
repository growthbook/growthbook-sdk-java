package growthbook.sdk.java.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder
@AllArgsConstructor
public class FeatureResult<ValueType> {
    @Nullable
    ValueType value;

    // TODO: on ->
    // The assigned value cast to a boolean
//    Boolean on;

    /**
     * One of "unknownFeature", "defaultValue", "force", or "experiment"
     */
    @Nullable
    FeatureResultSource source;

    /**
     * When source is "experiment", this will be an Experiment object
     */
    @Nullable
    Experiment experiment;

    // TODO: ExperimentResult experimentResult
    // When source is "experiment", this will be an ExperimentResult object
    // @Nullable
}
