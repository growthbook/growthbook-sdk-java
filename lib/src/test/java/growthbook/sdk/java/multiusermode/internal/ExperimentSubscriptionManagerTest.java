package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentSubscriptionManagerTest {

    @Test
    @DisplayName("Notifies subscribers only when an experiment's assignment changes")
    void notifiesOnlyOnAssignmentChange() {
        // Given
        ExperimentSubscriptionManager manager = new ExperimentSubscriptionManager();
        RecordingCallback callback = new RecordingCallback();
        manager.subscribe(callback);
        Experiment<String> experiment = experiment("exp-1");

        // When
        manager.publishIfChanged(experiment, result(1));
        manager.publishIfChanged(experiment, result(1));
        manager.publishIfChanged(experiment, result(0));

        // Then
        assertEquals(2, callback.runs.size());
    }

    @Test
    @DisplayName("Keeps an experiment without a key apart from one whose key is empty")
    void keepsKeylessExperimentApartFromEmptyKey() {
        // Given
        ExperimentSubscriptionManager manager = new ExperimentSubscriptionManager();
        RecordingCallback callback = new RecordingCallback();
        manager.subscribe(callback);

        // When
        manager.publishIfChanged(experiment(null), result(1));
        manager.publishIfChanged(experiment(""), result(1));

        // Then
        assertEquals(2, callback.runs.size());
    }

    @Test
    @DisplayName("Stops notifying after clear but keeps the remembered assignments")
    void clearRemovesCallbacksButKeepsAssignments() {
        // Given
        ExperimentSubscriptionManager manager = new ExperimentSubscriptionManager();
        RecordingCallback cleared = new RecordingCallback();
        manager.subscribe(cleared);
        manager.publishIfChanged(experiment("exp-1"), result(1));
        manager.clear();
        RecordingCallback later = new RecordingCallback();
        manager.subscribe(later);

        // When
        manager.publishIfChanged(experiment("exp-1"), result(1));

        // Then
        assertEquals(1, cleared.runs.size());
        assertEquals(0, later.runs.size());
    }

    @Test
    @DisplayName("Keeps notifying the other subscribers when one throws")
    void isolatesThrowingSubscriber() {
        // Given
        ExperimentSubscriptionManager manager = new ExperimentSubscriptionManager();
        manager.subscribe(new ThrowingCallback());
        RecordingCallback callback = new RecordingCallback();
        manager.subscribe(callback);

        // When
        manager.publishIfChanged(experiment("exp-1"), result(1));

        // Then
        assertEquals(1, callback.runs.size());
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder().key(key).build();
    }

    private static ExperimentResult<String> result(int variationId) {
        return ExperimentResult.<String>builder().inExperiment(true).variationId(variationId).build();
    }

    private static final class RecordingCallback implements ExperimentRunCallback {
        private final List<Integer> runs = new ArrayList<>();

        @Override
        public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> result) {
            runs.add(result.getVariationId());
        }
    }

    private static final class ThrowingCallback implements ExperimentRunCallback {
        @Override
        public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> result) {
            throw new IllegalStateException("subscriber failed");
        }
    }
}
