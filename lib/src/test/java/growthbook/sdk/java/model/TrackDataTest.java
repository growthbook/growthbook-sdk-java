package growthbook.sdk.java.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class TrackDataTest {

    @Test
    @DisplayName("Exposes the experiment and result it was constructed with")
    void exposesExperimentAndResult() {
        // Given
        Experiment<Integer> experiment = Experiment.<Integer>builder().key("experiment-1").force(100).build();
        ExperimentResult<Integer> result = ExperimentResult.<Integer>builder().inExperiment(true).key("experiment-1").build();

        // When
        TrackData<Integer> subject = new TrackData<>(experiment, result);

        // Then
        assertSame(experiment, subject.getExperiment());
        assertSame(result, subject.getResult());
    }
}
