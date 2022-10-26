package growthbook.sdk.java.models;

import growthbook.sdk.java.services.GrowthBookJsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class ContextTest {
    private AutoCloseable closeable;
    @Mock
    private TrackingCallback trackingCallback;

    HashMap<String, String> sampleUserAttributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        sampleUserAttributes.put("country", "canada");
        sampleUserAttributes.put("device", "android");
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void canBeConstructed() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";
        HashMap<String, Integer> forcedVariations = new HashMap<String, Integer>();
        HashMap<String, Feature> features = new HashMap<>();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);
        String featuresJson = "{}";

        Context subject = new Context(
                isEnabled,
                sampleUserAttributes,
                url,
                featuresJson,
                forcedVariations,
                isQaMode,
                trackingCallback
        );

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = new Context(
                isEnabled,
                sampleUserAttributes,
                url,
                "{}",
                new HashMap<>(),
                isQaMode,
                trackingCallback
        );

        // Initial state OK
        assertTrue(subject.getEnabled());
        assertFalse(subject.getIsQaMode());
        assertEquals("http://localhost:3000", subject.getUrl());

        // Use setters
        subject.setEnabled(false);
        subject.setIsQaMode(true);
        subject.setUrl("https://docs.growthbook.io/lib/build-your-own");

        assertFalse(subject.getEnabled());
        assertTrue(subject.getIsQaMode());
        assertEquals("https://docs.growthbook.io/lib/build-your-own", subject.getUrl());
    }

    @Test
    void canExecuteATrackingCallback() {
        Context subject = new Context(
                true,
                new HashMap<>(),
                null,
                "{}",
                new HashMap<>(),
                false,
                trackingCallback
        );

        Experiment<String> experiment = Experiment.<String>builder().build();
        ExperimentResult<String> result = ExperimentResult
                .<String>builder()
                .value("Hello, world!")
                .build();
        subject.getTrackingCallback().onTrack(experiment, result);

        verify(trackingCallback).onTrack(experiment, result);
    }
}
