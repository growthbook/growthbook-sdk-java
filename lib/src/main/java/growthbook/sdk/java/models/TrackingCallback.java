package growthbook.sdk.java.models;

public interface TrackingCallback {
    void onTrack(Experiment experiment, TrackingResult trackingResult);
}
