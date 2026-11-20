package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonParser;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EvaluationContextTest {

    @Test
    @DisplayName("Reports an exposure once when a directly built context is reused with an experiment evaluator")
    void reportsExposureOnceForDirectlyBuiltContext() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(Options.builder().trackingCallBackWithUser(tracking).build());
        ExperimentEvaluator evaluator = new ExperimentEvaluator();

        // When
        evaluator.evaluateExperiment(experiment(), context, null);
        evaluator.evaluateExperiment(experiment(), context, null);

        // Then
        assertEquals(Collections.singletonList("exp-1"), tracking.experimentKeys);
    }

    @Test
    @DisplayName("Reports every exposure when the experiment tracker is cleared")
    void reportsEveryExposureWhenTrackerCleared() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(Options.builder().trackingCallBackWithUser(tracking).build());
        context.setExperimentTracker(null);
        ExperimentEvaluator evaluator = new ExperimentEvaluator();

        // When
        evaluator.evaluateExperiment(experiment(), context, null);
        evaluator.evaluateExperiment(experiment(), context, null);

        // Then
        assertEquals(Arrays.asList("exp-1", "exp-1"), tracking.experimentKeys);
    }

    @Test
    @DisplayName("Starts with its own experiment tracker")
    void startsWithOwnTracker() {
        // Given
        EvaluationContext first = context(Options.builder().build());
        EvaluationContext second = context(Options.builder().build());

        // When
        ExperimentTracker firstTracker = first.getExperimentTracker();

        // Then
        assertNotNull(firstTracker);
        assertNotNull(second.getExperimentTracker());
        assertNotSame(firstTracker, second.getExperimentTracker());
    }

    @Test
    @DisplayName("Forwards the deprecated shared tracker accessors to the experiment tracker")
    @SuppressWarnings("deprecation")
    void forwardsDeprecatedSharedTrackerAccessors() {
        // Given
        EvaluationContext context = context(Options.builder().build());
        ExperimentTracker tracker = new ExperimentTracker();

        // When
        context.setSharedExperimentTracker(tracker);

        // Then
        assertSame(tracker, context.getExperimentTracker());
        assertSame(tracker, context.getSharedExperimentTracker());
    }

    @Test
    @DisplayName("Clears the experiment tracker through the deprecated shared tracker setter")
    @SuppressWarnings("deprecation")
    void clearsTrackerThroughDeprecatedSetter() {
        // Given
        EvaluationContext context = context(Options.builder().build());

        // When
        context.setSharedExperimentTracker(null);

        // Then
        assertNull(context.getExperimentTracker());
        assertNull(context.getSharedExperimentTracker());
    }

    private static EvaluationContext context(Options options) {
        UserContext user = UserContext.builder()
                .attributes(JsonParser.parseString("{\"id\": \"user-1\"}").getAsJsonObject())
                .build();
        return new EvaluationContext(GlobalContext.builder().build(), user, new EvaluationContext.StackContext(), options);
    }

    private static Experiment<String> experiment() {
        return Experiment.<String>builder()
                .key("exp-1")
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .build();
    }

    private static final class RecordingTrackingCallback implements TrackingCallbackWithUser {
        private final List<String> experimentKeys = new ArrayList<>();

        @Override
        public <T> void onTrack(Experiment<T> experiment,
                                ExperimentResult<T> experimentResult,
                                @Nullable UserContext userContext) {
            experimentKeys.add(experiment.getKey());
        }
    }
}
