package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        TrackData<Integer> subject = new TrackData<>(experiment, experimentResult);

        assertEquals(experiment, subject.getExperiment());
        assertEquals(experimentResult, subject.getExperimentResult());
    }
}
