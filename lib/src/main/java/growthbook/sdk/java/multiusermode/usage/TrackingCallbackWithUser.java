package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;

public interface TrackingCallbackWithUser {

    /**
     * This callback is called with the {@link Experiment} and {@link ExperimentResult} when an experiment is evaluated.
     *
     * @param <ValueType>      the value type for the experiment
     * @param experiment       the {@link Experiment}
     * @param experimentResult the {@link ExperimentResult}
     * @param userContext      the {@link UserContext}
     */
    <ValueType> void onTrack(Experiment<ValueType> experiment,
                             ExperimentResult<ValueType> experimentResult,
                             UserContext userContext);
}
