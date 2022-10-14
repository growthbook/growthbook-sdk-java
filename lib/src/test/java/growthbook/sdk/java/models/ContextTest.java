package growthbook.sdk.java.models;

import growthbook.sdk.java.TestHelpers.SampleUserAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContextTest {
    private AutoCloseable closeable;
    @Mock
    private TrackingCallback<String> trackingCallback;

    SampleUserAttributes sampleUserAttributes = new SampleUserAttributes("android", "canada");

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

        Context<String> subject = new Context<String>(isEnabled, url, isQaMode, trackingCallback, sampleUserAttributes);

        assertNotNull(subject);
    }

    @Test
    void canBeBuilt() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context<String> subject = Context
                .<String>builder()
                .enabled(isEnabled)
                .isQaMode(isQaMode)
                .attributes(sampleUserAttributes)
                .url(url)
                .build();

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context<String> subject = Context
                .<String>builder()
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
        Context<String> subject = Context
                .<String>builder()
                .attributes(sampleUserAttributes)
                .build();

        assertEquals("{\"device\":\"android\",\"country\":\"canada\"}", subject.attributes.toJson());
    }

    @Test
    void canExecuteATrackingCallback() {
        Context<String> subject = Context
                .<String>builder()
                .trackingCallback(trackingCallback)
                .build();

        Experiment experiment = Experiment.builder().build();
        TrackingResult<String> result = TrackingResult
                .<String>builder()
                .value("Hello, world!")
                .build();
        subject.trackingCallback.onTrack(experiment, result);

        verify(trackingCallback).onTrack(experiment, result);
    }
}