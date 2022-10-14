package growthbook.sdk.java.models;

public interface TrackingCallback<T> {
    void onTrack(Experiment experiment, TrackingResult<T> trackingResult);
}
