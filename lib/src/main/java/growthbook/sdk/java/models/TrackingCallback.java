package growthbook.sdk.java.models;

public interface TrackingCallback<ValueType> {
    void onTrack(Experiment experiment, TrackingResult<ValueType> trackingResult);
}
