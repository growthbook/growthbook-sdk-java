package growthbook.sdk.java.plugin;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

    private static final List<String> FULL_LIFECYCLE =
            Arrays.asList("init", "experimentViewed:exp-1", "featureEvaluated:flag-1", "close");

    @Test
    @DisplayName("Dispatches every lifecycle event to each registered plugin in order")
    void dispatchesEveryLifecycleEventToEachPlugin() {
        // Given
        RecordingPlugin first = new RecordingPlugin();
        RecordingPlugin second = new RecordingPlugin();
        PluginRegistry registry = new PluginRegistry(Arrays.asList(first, second));

        // When
        runFullLifecycle(registry);

        // Then
        assertEquals(FULL_LIFECYCLE, first.events);
        assertEquals(FULL_LIFECYCLE, second.events);
    }

    @ParameterizedTest(name = "init succeeds = {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("A plugin that throws does not stop the plugins registered after it")
    void throwingPluginDoesNotStopOtherPlugins(boolean initSucceeds) {
        // Given
        RecordingPlugin good = new RecordingPlugin();
        PluginRegistry registry = new PluginRegistry(Arrays.asList(new ThrowingPlugin(initSucceeds), good));

        // When
        runFullLifecycle(registry);

        // Then
        assertEquals(FULL_LIFECYCLE, good.events);
    }

    @Test
    @DisplayName("A plugin that throws after a successful init keeps receiving later events")
    void pluginThrowingOnEventsKeepsReceivingLaterEvents() {
        // Given
        ThrowingPlugin plugin = new ThrowingPlugin(true);
        PluginRegistry registry = new PluginRegistry(Collections.singletonList(plugin));

        // When
        runFullLifecycle(registry);

        // Then
        assertEquals(Arrays.asList("init", "experimentViewed", "featureEvaluated", "close"), plugin.attempts);
    }

    @Test
    @DisplayName("A plugin whose init fails is dropped and receives no further events")
    void pluginWhoseInitFailsReceivesNoFurtherEvents() {
        // Given
        ThrowingPlugin failsInit = new ThrowingPlugin(false);
        PluginRegistry registry = new PluginRegistry(Collections.singletonList(failsInit));

        // When
        runFullLifecycle(registry);

        // Then
        assertEquals(Collections.singletonList("init"), failsInit.attempts);
        assertTrue(registry.isEmpty());
    }

    @Test
    @DisplayName("Null plugin entries are ignored and the remaining plugins are dispatched to")
    void nullPluginEntriesAreFilteredOut() {
        // Given
        RecordingPlugin good = new RecordingPlugin();
        PluginRegistry registry = new PluginRegistry(Arrays.asList(null, good, null));

        // When
        runFullLifecycle(registry);

        // Then
        assertFalse(registry.isEmpty());
        assertEquals(FULL_LIFECYCLE, good.events);
    }

    @ParameterizedTest(name = "plugins = {0}")
    @NullAndEmptySource
    @DisplayName("A null or empty plugin list yields an empty registry whose dispatch is a no-op")
    void nullOrEmptyPluginListYieldsEmptyRegistry(List<GrowthBookPlugin> plugins) {
        // Given
        PluginRegistry registry = new PluginRegistry(plugins);

        // When / Then
        assertTrue(registry.isEmpty());
        assertDoesNotThrow(() -> runFullLifecycle(registry));
    }

    @Test
    @DisplayName("Forwards the user attributes to plugins that override the attribute-aware callbacks")
    void forwardsUserAttributesToAttributeAwareCallbacks() {
        // Given
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRegistry registry = new PluginRegistry(Collections.singletonList(plugin));
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "user-1");

        // When
        registry.fireExperimentViewed(experiment(), ExperimentResult.<String>builder().build(), attributes);
        registry.fireFeatureEvaluated("flag-1", featureResult(), attributes);

        // Then
        assertEquals(2, plugin.attributes.size());
        assertSame(attributes, plugin.attributes.get(0));
        assertSame(attributes, plugin.attributes.get(1));
    }

    private static void runFullLifecycle(PluginRegistry registry) {
        registry.initAll();
        registry.fireExperimentViewed(experiment(), ExperimentResult.<String>builder().build());
        registry.fireFeatureEvaluated("flag-1", featureResult());
        registry.closeAll();
    }

    private static Experiment<String> experiment() {
        return Experiment.<String>builder().key("exp-1").build();
    }

    private static FeatureResult<String> featureResult() {
        return FeatureResult.<String>builder().source(FeatureResultSource.DEFAULT_VALUE).build();
    }

    private static final class RecordingPlugin implements GrowthBookPlugin {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final List<JsonObject> attributes = new ArrayList<>();

        @Override
        public void init() {
            events.add("init");
        }

        @Override
        public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
            events.add("experimentViewed:" + experiment.getKey());
        }

        @Override
        public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result, JsonObject userAttributes) {
            if (userAttributes != null) {
                attributes.add(userAttributes);
            }
            onExperimentViewed(experiment, result);
        }

        @Override
        public <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result) {
            events.add("featureEvaluated:" + featureKey);
        }

        @Override
        public <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result, JsonObject userAttributes) {
            if (userAttributes != null) {
                attributes.add(userAttributes);
            }
            onFeatureEvaluated(featureKey, result);
        }

        @Override
        public void close() {
            events.add("close");
        }
    }

    private static final class ThrowingPlugin implements GrowthBookPlugin {
        private final boolean initSucceeds;
        private final List<String> attempts = new CopyOnWriteArrayList<>();

        ThrowingPlugin(boolean initSucceeds) {
            this.initSucceeds = initSucceeds;
        }

        @Override
        public void init() {
            attempts.add("init");
            if (!initSucceeds) {
                throw new IllegalStateException("init failed");
            }
        }

        @Override
        public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
            attempts.add("experimentViewed");
            throw new IllegalStateException("experimentViewed failed");
        }

        @Override
        public <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result) {
            attempts.add("featureEvaluated");
            throw new IllegalStateException("featureEvaluated failed");
        }

        @Override
        public void close() {
            attempts.add("close");
            throw new IllegalStateException("close failed");
        }
    }
}
