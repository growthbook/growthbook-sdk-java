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
                features,
                isEnabled,
                url,
                isQaMode,
                trackingCallback,
                sampleUserAttributes,
                featuresJson,
                forcedVariations
        );

        assertNotNull(subject);
    }

    @Test
    void canBeBuilt() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        HashMap<String, Integer> forcedVariations = new HashMap<String, Integer>();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);

        Context subject = Context
                .builder()
                .enabled(isEnabled)
                .isQaMode(isQaMode)
                .attributes(sampleUserAttributes)
                .forcedVariationsMap(forcedVariations)
                .url(url)
                .build();

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = Context
                .builder()
                .enabled(isEnabled)
                .isQaMode(isQaMode)
                .attributes(sampleUserAttributes)
                .url(url)
                .build();

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
    void serializableAttributes() {
        Context subject = Context
                .builder()
                .attributes(sampleUserAttributes)
                .build();
        String attributesJson = GrowthBookJsonUtils.getInstance().gson.toJson(subject.attributes);

        assertEquals("{\"country\":\"canada\",\"device\":\"android\"}", attributesJson);
    }

    @Test
    void canExecuteATrackingCallback() {
        Context subject = Context
                .builder()
                .trackingCallback(trackingCallback)
                .build();

        Experiment<String> experiment = Experiment.<String>builder().build();
        ExperimentResult<String> result = ExperimentResult
                .<String>builder()
                .value("Hello, world!")
                .build();
        subject.trackingCallback.onTrack(experiment, result);

        verify(trackingCallback).onTrack(experiment, result);
    }
}
