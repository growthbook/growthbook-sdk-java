package growthbook.sdk.java.plugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: plugins observe events from real {@link GrowthBook} evaluations,
 * and an existing {@link TrackingCallback}/{@link FeatureUsageCallback} still fires.
 */
class PluginIntegrationTest {

    @Test
    void pluginsObserveFeatureAndExperimentEvents() {
        List<FeatureResult<?>> featureSeen = Collections.synchronizedList(new ArrayList<>());
        List<ExperimentResult<?>> experimentSeen = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger closed = new AtomicInteger();

        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) { experimentSeen.add(r); }
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { featureSeen.add(r); }
            @Override public void close() { closed.incrementAndGet(); }
        };

        AtomicInteger trackingCallbackCalls = new AtomicInteger();
        AtomicInteger featureCallbackCalls = new AtomicInteger();
        TrackingCallback tc = new TrackingCallback() {
            @Override public <V> void onTrack(Experiment<V> experiment, ExperimentResult<V> result) {
                trackingCallbackCalls.incrementAndGet();
            }
        };
        FeatureUsageCallback fc = new FeatureUsageCallback() {
            @Override public <V> void onFeatureUsage(String key, FeatureResult<V> result) {
                featureCallbackCalls.incrementAndGet();
            }
        };

        String featuresJson = "{\"flag-a\": {\"defaultValue\": true}, \"flag-b\": {\"defaultValue\": \"x\"}}";
        GBContext ctx = GBContext.builder()
                .featuresJson(featuresJson)
                .attributesJson("{\"id\":\"u1\"}")
                .trackingCallback(tc)
                .featureUsageCallback(fc)
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBook gb = new GrowthBook(ctx);

        gb.isOn("flag-a");
        gb.evalFeature("flag-b", String.class);

        // Experiment with two variations; default id attribute is present, so it should be in-experiment.
        Experiment<String> exp = Experiment.<String>builder()
                .key("my-exp")
                .variations(new ArrayList<>(Arrays.asList("A", "B")))
                .build();
        ExperimentResult<String> result = gb.run(exp);

        gb.destroy();

        assertTrue(featureSeen.size() >= 2, "plugin should have seen at least 2 feature evaluations");
        assertTrue(featureCallbackCalls.get() >= 2, "existing feature usage callback should still fire");

        if (Boolean.TRUE.equals(result.getInExperiment())) {
            assertEquals(1, experimentSeen.size(), "plugin should have seen the experiment event exactly once");
            assertEquals(1, trackingCallbackCalls.get(), "existing tracking callback should still fire");
        }

        assertEquals(1, closed.get(), "plugin close() should fire when GrowthBook.destroy() is called");
    }

    @Test
    void repeatedExperimentEvaluationFiresExposureOnce() {
        List<ExperimentResult<?>> experimentSeen = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger trackingCallbackCalls = new AtomicInteger();

        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) { experimentSeen.add(r); }
        };
        TrackingCallback tc = new TrackingCallback() {
            @Override public <V> void onTrack(Experiment<V> experiment, ExperimentResult<V> result) {
                trackingCallbackCalls.incrementAndGet();
            }
        };

        GBContext ctx = GBContext.builder()
                .attributesJson("{\"id\":\"u1\"}")
                .trackingCallback(tc)
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBook gb = new GrowthBook(ctx);

        Experiment<String> exp = Experiment.<String>builder()
                .key("my-exp")
                .variations(new ArrayList<>(Arrays.asList("A", "B")))
                .build();

        // Evaluate the SAME experiment for the SAME user three times.
        ExperimentResult<String> result = gb.run(exp);
        gb.run(exp);
        gb.run(exp);

        gb.destroy();

        // Exposure must be de-duplicated per (hashAttribute, hashValue, key, variationId):
        // one plugin event and one tracking callback, not three.
        if (Boolean.TRUE.equals(result.getInExperiment())) {
            assertEquals(1, experimentSeen.size(), "plugin should see the exposure exactly once across repeated evaluations");
            assertEquals(1, trackingCallbackCalls.get(), "tracking callback should fire exactly once across repeated evaluations");
        }
    }

    @Test
    void pluginReceivesFeatureEventEvenWithoutExistingCallback() {
        List<String> keys = new ArrayList<>();
        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { keys.add(k); }
        };
        JsonObject attrs = JsonParser.parseString("{\"id\":\"u1\"}").getAsJsonObject();
        GBContext ctx = GBContext.builder()
                .featuresJson("{\"flag\": {\"defaultValue\": 42}}")
                .attributes(attrs)
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBook gb = new GrowthBook(ctx);

        FeatureResult<Integer> r = gb.evalFeature("flag", Integer.class);
        assertNotNull(r);
        assertTrue(keys.contains("flag"));
        gb.destroy();
    }

    @Test
    void forcedFeatureOverrideStillEmitsFeatureEvent() {
        List<FeatureResult<?>> seen = Collections.synchronizedList(new ArrayList<>());
        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { seen.add(r); }
        };
        GBContext ctx = GBContext.builder()
                .featuresJson("{\"flag\": {\"defaultValue\": false}}")
                .attributesJson("{\"id\":\"u1\"}")
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBook gb = new GrowthBook(ctx);

        Map<String, Object> forced = new HashMap<>();
        forced.put("flag", true);
        gb.setForcedFeatureValues(forced);

        FeatureResult<Boolean> r = gb.evalFeature("flag", Boolean.class);
        gb.destroy();

        assertEquals(Boolean.TRUE, r.getValue(), "forced override should win");
        assertEquals(1, seen.size(), "plugin should still receive feature_evaluated on the forced-override path");
        assertEquals(FeatureResultSource.OVERRIDE, seen.get(0).getSource());
    }
}
