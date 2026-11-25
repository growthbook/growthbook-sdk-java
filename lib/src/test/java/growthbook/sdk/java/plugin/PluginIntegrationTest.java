package growthbook.sdk.java.plugin;

import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugins observing real {@link GrowthBook} evaluations alongside the existing callbacks.
 */
class PluginIntegrationTest {

    @Test
    @DisplayName("Plugins observe feature and experiment events while the existing callbacks still fire")
    void pluginsObserveFeatureAndExperimentEventsAlongsideCallbacks() {
        // Given
        RecordingPlugin plugin = new RecordingPlugin();
        CountingTrackingCallback trackingCallback = new CountingTrackingCallback();
        CountingFeatureUsageCallback featureUsageCallback = new CountingFeatureUsageCallback();
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag-a\": {\"defaultValue\": true}, \"flag-b\": {\"defaultValue\": \"x\"}}")
                .attributesJson("{\"id\":\"user-1\"}")
                .trackingCallback(trackingCallback)
                .featureUsageCallback(featureUsageCallback)
                .plugins(Collections.singletonList(plugin))
                .build());

        // When
        growthBook.isOn("flag-a");
        growthBook.evalFeature("flag-b", String.class);
        ExperimentResult<String> result = growthBook.run(twoVariationExperiment());
        growthBook.destroy();

        // Then
        assertTrue(result.getInExperiment());
        assertEquals(Arrays.asList("flag-a", "flag-b"), plugin.featureKeys);
        assertEquals(2, featureUsageCallback.calls.get());
        assertEquals(Collections.singletonList("my-exp"), plugin.experimentKeys);
        assertEquals(1, trackingCallback.calls.get());
        assertEquals(1, plugin.closes.get());
    }

    @Test
    @DisplayName("A GrowthBook whose construction fails never starts its plugins")
    void failedConstructionNeverStartsPlugins() {
        // Given remote evaluation enabled without a client key, which construction rejects
        RecordingPlugin plugin = new RecordingPlugin();
        GBContext context = GBContext.builder()
                .remoteEval(true)
                .plugins(Collections.singletonList(plugin))
                .build();

        // When
        assertThrows(InvalidOptionsException.class, () -> new GrowthBook(context));

        // Then the plugin was never initialized, so nothing is left open without an instance to close it
        assertEquals(0, plugin.inits.get());
    }

    @Test
    @DisplayName("Repeated evaluation of the same experiment for the same user fires the exposure once")
    void repeatedExperimentEvaluationFiresExposureOnce() {
        // Given
        RecordingPlugin plugin = new RecordingPlugin();
        CountingTrackingCallback trackingCallback = new CountingTrackingCallback();
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .trackingCallback(trackingCallback)
                .plugins(Collections.singletonList(plugin))
                .build());
        Experiment<String> experiment = twoVariationExperiment();

        // When
        ExperimentResult<String> result = growthBook.run(experiment);
        growthBook.run(experiment);
        growthBook.run(experiment);
        growthBook.destroy();

        // Then
        assertTrue(result.getInExperiment());
        assertEquals(Collections.singletonList("my-exp"), plugin.experimentKeys);
        assertEquals(1, trackingCallback.calls.get());
    }

    @Test
    @DisplayName("A plugin receives feature events when no feature usage callback is configured")
    void pluginReceivesFeatureEventWithoutFeatureUsageCallback() {
        // Given
        RecordingPlugin plugin = new RecordingPlugin();
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\": {\"defaultValue\": 42}}")
                .attributesJson("{\"id\":\"user-1\"}")
                .plugins(Collections.singletonList(plugin))
                .build());

        // When
        growthBook.evalFeature("flag", Integer.class);
        growthBook.destroy();

        // Then
        assertEquals(Collections.singletonList("flag"), plugin.featureKeys);
    }

    @Test
    @DisplayName("A forced feature override still emits a feature event with the override source")
    void forcedFeatureOverrideStillEmitsFeatureEvent() {
        // Given
        RecordingPlugin plugin = new RecordingPlugin();
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\": {\"defaultValue\": false}}")
                .attributesJson("{\"id\":\"user-1\"}")
                .plugins(Collections.singletonList(plugin))
                .build());
        Map<String, Object> forced = new HashMap<>();
        forced.put("flag", true);
        growthBook.setForcedFeatureValues(forced);

        // When
        FeatureResult<Boolean> result = growthBook.evalFeature("flag", Boolean.class);
        growthBook.destroy();

        // Then
        assertEquals(Boolean.TRUE, result.getValue());
        assertEquals(1, plugin.featureResults.size());
        assertEquals(FeatureResultSource.OVERRIDE, plugin.featureResults.get(0).getSource());
    }

    private static Experiment<String> twoVariationExperiment() {
        return Experiment.<String>builder()
                .key("my-exp")
                .variations(new ArrayList<>(Arrays.asList("A", "B")))
                .build();
    }

    private static final class RecordingPlugin implements GrowthBookPlugin {
        private final List<String> featureKeys = new CopyOnWriteArrayList<>();
        private final List<FeatureResult<?>> featureResults = new CopyOnWriteArrayList<>();
        private final List<String> experimentKeys = new CopyOnWriteArrayList<>();
        private final AtomicInteger inits = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public void init() {
            inits.incrementAndGet();
        }

        @Override
        public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
            experimentKeys.add(experiment.getKey());
        }

        @Override
        public <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result) {
            featureKeys.add(featureKey);
            featureResults.add(result);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    private static final class CountingTrackingCallback implements TrackingCallback {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public <V> void onTrack(Experiment<V> experiment, ExperimentResult<V> result) {
            calls.incrementAndGet();
        }
    }

    private static final class CountingFeatureUsageCallback implements FeatureUsageCallback {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public <V> void onFeatureUsage(String featureKey, FeatureResult<V> result) {
            calls.incrementAndGet();
        }
    }
}
