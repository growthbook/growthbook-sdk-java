package growthbook.sdk.java;

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

        GBContext subject = new GBContext(
                sampleUserAttributes,
                featuresJson,
                null,
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

    @Test
    void supportsEncryptedFeaturesUsingConstructor() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";
        HashMap<String, Integer> forcedVariations = new HashMap<String, Integer>();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);
        String encryptedFeaturesJson = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        GBContext subject = new GBContext(
                sampleUserAttributes,
                encryptedFeaturesJson,
                encryptionKey,
                isEnabled,
                isQaMode,
                url,
                forcedVariations,
                trackingCallback
        );
        String expectedFeaturesJson = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";

        assertNotNull(subject);
        assertEquals(expectedFeaturesJson.trim(), subject.getFeaturesJson().trim());
    }

    @Test
    void supportsEncryptedFeaturesUsingBuilder() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";
        HashMap<String, Integer> forcedVariations = new HashMap<String, Integer>();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);
        String encryptedFeaturesJson = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        GBContext subject = GBContext
                .builder()
                .enabled(isEnabled)
                .attributesJson(sampleUserAttributes)
                .url(url)
                .featuresJson(encryptedFeaturesJson)
                .encryptionKey(encryptionKey)
                .forcedVariationsMap(new HashMap<>())
                .isQaMode(isQaMode)
                .trackingCallback(trackingCallback)
                .build();

        String expectedFeaturesJson = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";

        assertNotNull(subject);
        assertEquals(expectedFeaturesJson.trim(), subject.getFeaturesJson().trim());
    }
}
