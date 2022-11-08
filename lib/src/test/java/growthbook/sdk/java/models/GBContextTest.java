package growthbook.sdk.java.models;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class GBContextTest {
    private AutoCloseable closeable;
    @Mock
    private TrackingCallback trackingCallback;

    final String sampleUserAttributes = "{\"country\": \"canada\", \"device\": \"android\"}";

    @BeforeEach
    void setUp() {
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
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);
        String featuresJson = "{}";

        GBContext subject = GBContext.create(
                sampleUserAttributes,
                featuresJson,
                isEnabled,
                isQaMode,
                url,
                forcedVariations,
                trackingCallback
        );

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        GBContext subject = GBContext
                .builder()
                .enabled(isEnabled)
                .attributesJson(sampleUserAttributes)
                .url(url)
                .featuresJson("{}")
                .forcedVariationsMap(new HashMap<>())
                .isQaMode(isQaMode)
                .trackingCallback(trackingCallback)
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
    void canExecuteATrackingCallback() {
        GBContext subject = GBContext
                .builder()
                .trackingCallback(trackingCallback)
                .build();

        Experiment<String> experiment = Experiment.<String>builder().build();
        ExperimentResult<String> result = ExperimentResult
                .<String>builder()
                .value("Hello, world!")
                .build();
        subject.getTrackingCallback().onTrack(experiment, result);

        verify(trackingCallback).onTrack(experiment, result);
    }
}
