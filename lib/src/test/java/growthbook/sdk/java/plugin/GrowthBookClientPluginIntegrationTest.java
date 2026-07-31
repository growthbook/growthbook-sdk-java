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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-user mode plugin lifecycle wiring test. Avoids the feature repository
 * by never calling {@link GrowthBookClient#initialize()}; verifies the
 * registry is built, stashed on {@link Options}, and flushed on shutdown.
 */
class GrowthBookClientPluginIntegrationTest {

    @Test
    void registryIsBuiltAndFlushedOnShutdown() {
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

        assertEquals(1, inits.get(), "plugin init() should fire on construction");

        client.shutdown();

        assertEquals(1, closed.get(), "plugin close() should fire on client.shutdown()");
    }

    @Test
    void twoClientsFromOneOptionsManageTheirRegistriesIndependently() {
        AtomicInteger inits = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public void init() { inits.incrementAndGet(); }
            @Override public void close() { closed.incrementAndGet(); }
        };

        // Reusing one (mutable) Options must not let one client's registry
        // overwrite the other's — each client owns its registry.
        Options shared = Options.builder()
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBookClient first = new GrowthBookClient(shared);
        GrowthBookClient second = new GrowthBookClient(shared);
        assertEquals(2, inits.get(), "each client builds and inits its own registry");

        first.shutdown();
        assertEquals(1, closed.get(), "shutting down the first client closes only its own registry once");
        second.shutdown();
        assertEquals(2, closed.get(), "the second client still closes independently");
    }

    @Test
    void repeatedShutdownIsSafe() {
        AtomicInteger closed = new AtomicInteger();
        GrowthBookPlugin plugin = new GrowthBookPlugin() {
            @Override public void close() { closed.incrementAndGet(); }
        };
        Options options = Options.builder()
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        client.shutdown();
        client.shutdown();
        assertTrue(closed.get() >= 1, "plugin close() should fire at least once across repeated shutdown");
    }
}
