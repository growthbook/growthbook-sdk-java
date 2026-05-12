package growthbook.sdk.java.plugin;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Multi-user mode plugin lifecycle wiring test. Avoids the feature repository
 * by never calling {@link GrowthBookClient#initialize()}; verifies the
 * registry is built, stashed on {@link Options}, and flushed on close.
 */
class GrowthBookClientPluginIntegrationTest {

    @Test
    void registryIsBuiltAndFlushedOnClose() {
        AtomicInteger inits = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();

        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public void init() { inits.incrementAndGet(); }
            @Override public <V> void onExperimentViewed(Experiment<V> e, ExperimentResult<V> r) {}
            @Override public <V> void onFeatureEvaluated(String k, FeatureResult<V> r) {}
            @Override public void close() { closed.incrementAndGet(); }
        };

        Options options = Options.builder()
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        assertNotNull(options.getPluginRegistry(), "registry should be stashed on options");
        assertEquals(1, inits.get(), "plugin init() should fire on construction");

        client.close();

        assertEquals(1, closed.get(), "plugin close() should fire on client.close()");
    }

    @Test
    void tripleCloseIsSafe() {
        GrowthBookPlugin plugin = new GrowthBookPlugin() {};
        Options options = Options.builder()
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        client.close();
        client.close();
        client.shutdown();
        assertSame(options, options, "no exception on repeated close/shutdown");
    }
}
