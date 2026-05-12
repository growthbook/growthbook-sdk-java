package growthbook.sdk.java.plugin;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginRegistryTest {

    @Test
    void dispatchesToEachPlugin() {
        AtomicInteger exp = new AtomicInteger();
        AtomicInteger feat = new AtomicInteger();
        AtomicInteger inits = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        GrowthBookPlugin a = new GrowthBookPlugin() {
            @Override public void init() { inits.incrementAndGet(); }
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) { exp.incrementAndGet(); }
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { feat.incrementAndGet(); }
            @Override public void close() { closes.incrementAndGet(); }
        };
        GrowthBookPlugin b = new GrowthBookPlugin() {
            @Override public void init() { inits.incrementAndGet(); }
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) { exp.incrementAndGet(); }
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { feat.incrementAndGet(); }
            @Override public void close() { closes.incrementAndGet(); }
        };

        PluginRegistry registry = new PluginRegistry(Arrays.asList(a, b));
        registry.initAll();
        registry.fireExperimentViewed(Experiment.<String>builder().key("e").build(), ExperimentResult.<String>builder().build());
        registry.fireFeatureEvaluated("f", FeatureResult.<String>builder().source(FeatureResultSource.DEFAULT_VALUE).build());
        registry.closeAll();

        assertEquals(2, inits.get());
        assertEquals(2, exp.get());
        assertEquals(2, feat.get());
        assertEquals(2, closes.get());
    }

    @Test
    void onePluginThrowingDoesNotStopOthers() {
        AtomicInteger calls = new AtomicInteger();
        GrowthBookPlugin bad = new GrowthBookPlugin() {
            @Override public void init() { throw new RuntimeException("boom"); }
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) { throw new RuntimeException("boom"); }
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { throw new RuntimeException("boom"); }
            @Override public void close() { throw new RuntimeException("boom"); }
        };
        GrowthBookPlugin good = new GrowthBookPlugin() {
            @Override public void init() { calls.incrementAndGet(); }
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) { calls.incrementAndGet(); }
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) { calls.incrementAndGet(); }
            @Override public void close() { calls.incrementAndGet(); }
        };

        PluginRegistry registry = new PluginRegistry(Arrays.asList(bad, good));
        registry.initAll();
        registry.fireExperimentViewed(Experiment.<String>builder().key("e").build(), ExperimentResult.<String>builder().build());
        registry.fireFeatureEvaluated("f", FeatureResult.<String>builder().build());
        registry.closeAll();

        assertEquals(4, calls.get(), "good plugin should have received all 4 lifecycle events");
    }

    @Test
    void emptyRegistryIsNoOp() {
        PluginRegistry registry = new PluginRegistry(Collections.emptyList());
        registry.initAll();
        registry.fireExperimentViewed(Experiment.<String>builder().key("e").build(), ExperimentResult.<String>builder().build());
        registry.fireFeatureEvaluated("f", FeatureResult.<String>builder().build());
        registry.closeAll();
    }
}
