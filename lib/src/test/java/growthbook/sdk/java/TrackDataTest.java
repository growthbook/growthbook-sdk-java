package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.TrackData;
import org.junit.jupiter.api.Test;

class TrackDataTest {
    @Test
    void canBeConstructed() {
        Experiment<Integer> experiment = Experiment.<Integer>builder()
                .key("my_experiment")
                .force(100)
                .build();
        ExperimentResult<Integer> experimentResult = ExperimentResult.<Integer>builder()
                .inExperiment(true)
                .key("my_experiment")
                .build();
        FeatureResult<Integer> featureResult = FeatureResult.<Integer>builder()
                .experimentResult(experimentResult)
                .build();

        TrackData<Integer> subject = new TrackData<>(experiment, featureResult);

        assertEquals(experiment, subject.getExperiment());
        assertEquals(experimentResult, subject.getResult().getExperimentResult());
    }
}
