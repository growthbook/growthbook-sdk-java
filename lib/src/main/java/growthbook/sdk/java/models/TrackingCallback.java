package growthbook.sdk.java.models;

public interface TrackingCallback {
    <ValueType> void onTrack(Experiment<ValueType> experiment, ExperimentResult<ValueType> experimentResult);
}
