package growthbook.sdk.java;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used for remote feature evaluation to trigger the {@link TrackingCallback}
 */
@AllArgsConstructor
@Getter
public class TrackData<ValueType> {
    Experiment<ValueType> experiment;
    ExperimentResult<ValueType> experimentResult;
}
