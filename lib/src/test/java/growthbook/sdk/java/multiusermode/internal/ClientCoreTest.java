package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientCoreTest {

    @Test
    @DisplayName("Initializes the plugins when created")
    void initializesPlugins() {
        // Given
        List<String> calls = new ArrayList<>();

        // When
        new ClientCore(Collections.<GrowthBookPlugin>singletonList(new RecordingPlugin(calls)));

        // Then
        assertEquals(Collections.singletonList("init"), calls);
    }

    @Test
    @DisplayName("Gives every scoped context the same tracker and the instance's plugins")
    void scopesContextsToInstance() {
        // Given
        ClientCore core = new ClientCore(null);

        // When
        EvaluationContext first = core.scope(context());
        EvaluationContext second = core.scope(context());

        // Then
        assertNotNull(first.getExperimentTracker());
        assertSame(first.getExperimentTracker(), second.getExperimentTracker());
        assertSame(first.getPluginRegistry(), second.getPluginRegistry());
    }

    @Test
    @DisplayName("Notifies subscribers once per assignment when running an experiment")
    void notifiesSubscribersOncePerAssignment() {
        // Given
        ClientCore core = new ClientCore(null);
        List<Integer> runs = new ArrayList<>();
        core.subscribe(new growthbook.sdk.java.callback.ExperimentRunCallback() {
            @Override
            public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> result) {
                runs.add(result.getVariationId());
            }
        });
        Experiment<String> experiment = Experiment.<String>builder()
                .key("exp-1")
                .variations(new ArrayList<>(Arrays.asList("a", "b")))
                .build();

        // When
        core.run(experiment, core.scope(context()));
        core.run(experiment, core.scope(context()));

        // Then
        assertEquals(1, runs.size());
    }

    @Test
    @DisplayName("Closes the plugins before the other resources, and keeps going when a step fails")
    void closesPluginsFirstAndContinuesAfterFailure() {
        // Given
        List<String> calls = new ArrayList<>();
        ClientCore core = new ClientCore(Collections.<GrowthBookPlugin>singletonList(new RecordingPlugin(calls)));
        calls.clear();

        // When
        core.close(
                () -> calls.add("remote eval"),
                () -> {
                    throw new IllegalStateException("repository failed to stop");
                },
                () -> calls.add("listener executor"));

        // Then
        assertEquals(Arrays.asList("close", "remote eval", "listener executor"), calls);
    }

    private static EvaluationContext context() {
        return new EvaluationContext(
                GlobalContext.builder().features(Collections.emptyMap()).build(),
                UserContext.builder().attributesJson("{\"id\":\"user-1\"}").build(),
                Options.builder().build());
    }

    private static final class RecordingPlugin implements GrowthBookPlugin {
        private final List<String> calls;

        RecordingPlugin(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void init() {
            calls.add("init");
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }
}
