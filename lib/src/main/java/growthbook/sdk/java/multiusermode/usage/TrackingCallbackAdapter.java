package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.multiusermode.configurations.UserContext;

// Use Wrapper adapter design pattern - to allow an existing interface to function as if it was a new interface
// to maintain 100% backward compatibility.
public class TrackingCallbackAdapter implements TrackingCallbackWithUser {

    private final TrackingCallback trackingCallback;

    // Constructor takes the old callback
    public TrackingCallbackAdapter(TrackingCallback trackingCallback) {
        this.trackingCallback = trackingCallback;
    }

    @Override
    public <ValueType> void onTrack(Experiment<ValueType> experiment, ExperimentResult<ValueType> experimentResult, UserContext userContext) {
        // Delegate call to the old callback, ignoring the new userContext parameter
        if (this.trackingCallback != null) {
            this.trackingCallback.onTrack(experiment, experimentResult);
        }

    }
}
