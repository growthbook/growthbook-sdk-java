package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.TypedKey;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockBuilder;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class UserScopedGrowthBookTest {

    @Test
    @DisplayName("Verify: a scoped run buffers the exposure until fireDeferredTrackingCalls is called")
    void scopedRunBuffersUntilFlush() {
        AtomicInteger trackCount = new AtomicInteger();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(createMockRepository());
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(tracker(trackCount), null));
            assertTrue(client.initialize());

            // When
            UserScopedGrowthBook user = client.forUser(userContext());
            ExperimentResult<String> result = user.run(experiment());

            // Then nothing fired yet, but the exposure is buffered
            assertTrue(result.getInExperiment());
            assertEquals(0, trackCount.get());
            assertEquals(1, user.getDeferredTrackingCalls().size());

            // When flushed
            user.fireDeferredTrackingCalls();

            // Then it fires once and drains
            assertEquals(1, trackCount.get());
            assertTrue(user.getDeferredTrackingCalls().isEmpty());
        }
    }

    @Test
    @DisplayName("Verify: a direct client run still fires the tracking callback immediately")
    void directClientRunFiresImmediately() {
        AtomicInteger trackCount = new AtomicInteger();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(createMockRepository());
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(tracker(trackCount), null));
            assertTrue(client.initialize());

            // When
            client.run(experiment(), userContext());

            // Then
            assertEquals(1, trackCount.get());
        }
    }

    @Test
    @DisplayName("Verify: a scoped logEvent fires the event logger immediately")
    void scopedLogEventFiresEventLogger() {
        AtomicReference<String> eventName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> eventProps = new AtomicReference<>();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(createMockRepository());
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(null, (name, props, user) -> {
                eventName.set(name);
                eventProps.set(props);
            }));
            assertTrue(client.initialize());

            Map<String, Object> props = new HashMap<>();
            props.put("plan", "pro");

            // When
            client.forUser(userContext()).logEvent("upgrade_clicked", props);

            // Then
            assertEquals("upgrade_clicked", eventName.get());
            assertEquals("pro", eventProps.get().get("plan"));
        }
    }

    @Test
    @DisplayName("Verify: scoped feature evaluation returns values and typed getters resolve")
    void scopedFeatureEvaluationReturnsValues() {
        String featuresJson = "{\"flag-on\":{\"defaultValue\":true},"
                + "\"count\":{\"defaultValue\":7},"
                + "\"theme\":{\"defaultValue\":\"dark\"}}";
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                createMockBuilder(repositoryWithFeatures(featuresJson));
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(null, null));
            assertTrue(client.initialize());
            UserScopedGrowthBook user = client.forUser(userContext());

            // String keys
            assertTrue(user.isOn("flag-on"));
            assertFalse(user.isOff("flag-on"));
            assertTrue(user.evalFeature("flag-on", Boolean.class).isOn());
            assertEquals(Integer.valueOf(7), user.getFeatureValue("count", 0, Integer.class));

            // Typed keys
            assertTrue(user.isOn(TypedKey.ofBoolean("flag-on")));
            assertEquals(Integer.valueOf(7), user.getFeatureValue(TypedKey.ofInteger("count"), 0));
            assertEquals("dark", user.getFeatureValue(TypedKey.ofString("theme"), "light"));
        }
    }

    @Test
    @DisplayName("Verify: a scoped missing feature evaluates off and returns the default value")
    void scopedMissingFeatureReturnsDefault() {
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                createMockBuilder(repositoryWithFeatures("{\"flag-on\":{\"defaultValue\":true}}"));
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(null, null));
            assertTrue(client.initialize());
            UserScopedGrowthBook user = client.forUser(userContext());

            assertFalse(user.isOn("missing"));
            assertTrue(user.isOff("missing"));
            assertEquals(Integer.valueOf(42), user.getFeatureValue("missing", 42, Integer.class));
            FeatureResult<Object> result = user.evalFeature("missing", Object.class);
            assertFalse(result.isOn());
            assertNull(result.getValue());
        }
    }

    @Test
    @DisplayName("Verify: scoped feature methods tolerate a null key without throwing")
    void scopedNullKeyIsTreatedAsMissing() {
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                createMockBuilder(repositoryWithFeatures("{\"flag-on\":{\"defaultValue\":true}}"));
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(null, null));
            assertTrue(client.initialize());
            UserScopedGrowthBook user = client.forUser(userContext());

            assertFalse(user.isOn((String) null));
            assertTrue(user.isOff((String) null));
            assertEquals("fallback", user.getFeatureValue((String) null, "fallback", String.class));
            assertNull(user.evalFeature(null, Object.class).getValue());
        }
    }

    @Test
    @DisplayName("Verify: an experiment-backed feature evaluated through a scope buffers the exposure until flush")
    void scopedExperimentBackedFeatureBuffersExposure() {
        AtomicInteger trackCount = new AtomicInteger();
        String featuresJson = "{\"exp-feature\":{\"defaultValue\":\"control\","
                + "\"rules\":[{\"variations\":[\"control\",\"variant\"]}]}}";
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                createMockBuilder(repositoryWithFeatures(featuresJson));
        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(optionsWith(tracker(trackCount), null));
            assertTrue(client.initialize());
            UserScopedGrowthBook user = client.forUser(userContext());

            // When the feature's experiment rule buckets the user
            FeatureResult<String> result = user.evalFeature("exp-feature", String.class);

            // Then the exposure is buffered, not fired
            assertNotNull(result.getExperimentResult());
            assertEquals(0, trackCount.get());
            assertEquals(1, user.getDeferredTrackingCalls().size());

            // When flushed, it fires once and drains
            user.fireDeferredTrackingCalls();
            assertEquals(1, trackCount.get());
            assertTrue(user.getDeferredTrackingCalls().isEmpty());
        }
    }

    private GBFeaturesRepository repositoryWithFeatures(String featuresJson) {
        GBFeaturesRepository repository = createMockRepository();
        Map<String, Feature<?>> parsed = TransformationUtil.transformFeatures(featuresJson);
        when(repository.getParsedFeatures()).thenReturn(parsed);
        when(repository.getFeatureSnapshot())
                .thenReturn(FeatureSnapshot.of(featuresJson, "{}", parsed, new JsonObject()));
        when(repository.getActiveFeatureCount()).thenReturn(parsed.size());
        return repository;
    }

    private Options optionsWith(TrackingCallbackWithUser tracking,
                                growthbook.sdk.java.multiusermode.usage.EventLogger eventLogger) {
        return Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .decryptionKey("test_key")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .featureRefreshListenerExecutor(Runnable::run)
                .trackingCallBackWithUser(tracking)
                .eventLogger(eventLogger)
                .build();
    }

    private UserContext userContext() {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "user-1");
        return UserContext.builder().attributes(attributes).build();
    }

    private Experiment<String> experiment() {
        return Experiment.<String>builder()
                .key("perf-experiment")
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
    }

    private static TrackingCallbackWithUser tracker(AtomicInteger counter) {
        return new TrackingCallbackWithUser() {
            @Override
            public <T> void onTrack(Experiment<T> experiment,
                                    ExperimentResult<T> result,
                                    UserContext userContext) {
                counter.incrementAndGet();
            }
        };
    }
}
