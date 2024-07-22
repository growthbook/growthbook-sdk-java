package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;

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
        Boolean allowUrlOverride = false;
        String url = "http://localhost:3000";
        HashMap<String, Integer> forcedVariations = new HashMap<String, Integer>();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);
        String featuresJson = "{}";

        GBContext subject = new GBContext(
                sampleUserAttributes,
                null,
                featuresJson,
                null,
                null,
                isEnabled,
                isQaMode,
                url,
                allowUrlOverride,
                forcedVariations,
                trackingCallback,
                null,
                null,
                null,
                null,
                null
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
        assertEquals(Boolean.TRUE, subject.getEnabled());
        assertFalse(subject.getIsQaMode());
        assertEquals("http://localhost:3000", subject.getUrl());

        // Use setters
        subject.setEnabled(false);
        subject.setIsQaMode(true);
        subject.setUrl("https://docs.growthbook.io/lib/build-your-own");

        assertNotEquals(Boolean.TRUE, subject.getEnabled());
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
        Boolean allowUrlOverride = false;
        String url = "http://localhost:3000";
        HashMap<String, Integer> forcedVariations = new HashMap<String, Integer>();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);
        String encryptedFeaturesJson = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        GBContext subject = new GBContext(
                sampleUserAttributes,
                null,
                encryptedFeaturesJson,
                null,
                encryptionKey,
                isEnabled,
                isQaMode,
                url,
                allowUrlOverride,
                forcedVariations,
                trackingCallback,
                null,
                null,
                null,
                null,
                null
        );
        String expectedFeaturesJson = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";

        assertNotNull(subject);
        assertEquals(expectedFeaturesJson.trim(), subject.getFeatures().toString().trim());
    }

    @Test
    void supportsEncryptedFeaturesUsingBuilder() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        Boolean allowUrlOverride = false;
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
                .allowUrlOverrides(allowUrlOverride)
                .featuresJson(encryptedFeaturesJson)
                .encryptionKey(encryptionKey)
                .forcedVariationsMap(forcedVariations)
                .isQaMode(isQaMode)
                .trackingCallback(trackingCallback)
                .build();

        String expectedFeaturesJson = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";

        assertNotNull(subject);
        assert subject.getFeatures() != null;
        assertEquals(expectedFeaturesJson.trim(), subject.getFeatures().toString().trim());
    }

    @Test
    void whenEncryptionKeyInvalid_featuresStayEmpty() {
        String encryptedFeaturesJson = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "nope";

        GBContext subject = GBContext
                .builder()
                .attributesJson(sampleUserAttributes)
                .featuresJson(encryptedFeaturesJson)
                .encryptionKey(encryptionKey)
                .build();

        assertEquals("{}", subject.getFeatures().toString());
    }

    @Test
    void whenEncryptedPayloadMalformed_featuresStayEmpty() {
        String encryptedFeaturesJson = "foo.bar.baz.==.ow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        GBContext subject = GBContext
                .builder()
                .attributesJson(sampleUserAttributes)
                .featuresJson(encryptedFeaturesJson)
                .encryptionKey(encryptionKey)
                .build();

        assertEquals("{}", subject.getFeatures().toString());
    }
}
