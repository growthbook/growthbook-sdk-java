package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

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
