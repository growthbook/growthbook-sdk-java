package growthbook.sdk.java.model;

import growthbook.sdk.java.callback.TrackingCallback;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used for remote feature evaluation to trigger the {@link TrackingCallback}
 */
@AllArgsConstructor
@Getter
public class TrackData<ValueType> {
    Experiment<ValueType> experiment;
    FeatureResult<ValueType> result;
}
