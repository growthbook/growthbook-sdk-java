package growthbook.sdk.java.plugin;

import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookClientPluginIntegrationTest {

    @Test
    @DisplayName("Initializes plugins on construction and closes them on shutdown")
    void initializesPluginsOnConstructionAndClosesThemOnShutdown() {
        // Given
        LifecyclePlugin plugin = new LifecyclePlugin();

        // When
        GrowthBookClient client = new GrowthBookClient(optionsWith(plugin));
        int initsAfterConstruction = plugin.inits.get();
        int closesBeforeShutdown = plugin.closes.get();
        client.shutdown();

        // Then
        assertEquals(1, initsAfterConstruction);
        assertEquals(0, closesBeforeShutdown);
        assertEquals(1, plugin.closes.get());
    }

    @Test
    @DisplayName("Two clients built from one Options instance manage their plugin registries independently")
    void twoClientsFromOneOptionsManageTheirRegistriesIndependently() {
        // Given
        LifecyclePlugin plugin = new LifecyclePlugin();
        Options shared = optionsWith(plugin);
        GrowthBookClient first = new GrowthBookClient(shared);
        GrowthBookClient second = new GrowthBookClient(shared);

        // When
        first.shutdown();
        int closesAfterFirstShutdown = plugin.closes.get();
        second.shutdown();

        // Then
        assertEquals(2, plugin.inits.get());
        assertEquals(1, closesAfterFirstShutdown);
        assertEquals(2, plugin.closes.get());
    }

    @Test
    @DisplayName("Shutting a client down twice is safe")
    void repeatedShutdownIsSafe() {
        // Given
        LifecyclePlugin plugin = new LifecyclePlugin();
        GrowthBookClient client = new GrowthBookClient(optionsWith(plugin));
        client.shutdown();

        // When / Then
        assertDoesNotThrow(client::shutdown);
        assertTrue(plugin.closes.get() >= 1);
    }

    private static Options optionsWith(GrowthBookPlugin plugin) {
        return Options.builder()
                .plugins(Collections.singletonList(plugin))
                .build();
    }

    private static final class LifecyclePlugin implements GrowthBookPlugin {
        private final AtomicInteger inits = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public void init() {
            inits.incrementAndGet();
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
