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
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

        gb.close();

        assertTrue(featureSeen.size() >= 2, "plugin should have seen at least 2 feature evaluations");
        assertTrue(featureCallbackCalls.get() >= 2, "existing feature usage callback should still fire");

        if (Boolean.TRUE.equals(result.getInExperiment())) {
            assertEquals(1, experimentSeen.size(), "plugin should have seen the experiment event exactly once");
            assertEquals(1, trackingCallbackCalls.get(), "existing tracking callback should still fire");
        }

        assertEquals(1, closed.get(), "plugin close() should fire when GrowthBook.close() is called");
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
        gb.close();
    }

    @Test
    void contextAwarePluginReceivesAttributesForOwningInstanceOnly() {
        List<String> firstInstanceIds = new ArrayList<>();
        List<String> secondInstanceIds = new ArrayList<>();

        GrowthBookPlugin firstPlugin = new GrowthBookPlugin() {
            @Override
            public <V> void onFeatureEvaluated(String k, FeatureResult<V> r, EvaluationContext context) {
                firstInstanceIds.add(context.getUser().getAttributes().get("id").getAsString());
            }
        };
        GrowthBookPlugin secondPlugin = new GrowthBookPlugin() {
            @Override
            public <V> void onFeatureEvaluated(String k, FeatureResult<V> r, EvaluationContext context) {
                secondInstanceIds.add(context.getUser().getAttributes().get("id").getAsString());
            }
        };

        GrowthBook first = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\": {\"defaultValue\": true}}")
                .attributesJson("{\"id\":\"first\"}")
                .plugins(Collections.singletonList(firstPlugin))
                .build());
        GrowthBook second = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\": {\"defaultValue\": true}}")
                .attributesJson("{\"id\":\"second\"}")
                .plugins(Collections.singletonList(secondPlugin))
                .build());

        first.isOn("flag");
        second.isOn("flag");
        first.close();
        second.close();

        assertEquals(Collections.singletonList("first"), firstInstanceIds);
        assertEquals(Collections.singletonList("second"), secondInstanceIds);
    }
}
