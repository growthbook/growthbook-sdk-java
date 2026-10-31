package growthbook.sdk.java.model;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GBContextTest {

    private static final String ATTRIBUTES_JSON = "{\"country\": \"canada\", \"device\": \"android\"}";
    private static final String ENCRYPTED_FEATURES = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
    private static final String ENCRYPTION_KEY = "BhB1wORFmZLTDjbvstvS8w==";
    private static final String DECRYPTED_FEATURES = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":["
            + "{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},"
            + "{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";

    @Test
    @DisplayName("The constructor keeps the given settings")
    void constructorKeepsGivenSettings() {
        // Given
        TrackingCallback trackingCallback = mock(TrackingCallback.class);
        HashMap<String, Integer> forcedVariations = new HashMap<>();
        forcedVariations.put("experiment-1", 0);
        forcedVariations.put("experiment-2", 1);

        // When
        GBContext subject = new GBContext(ATTRIBUTES_JSON, null, "{}", null, null, true, false,
                "http://localhost:3000", false, forcedVariations, trackingCallback, null, null, null, null, null);

        // Then
        assertEquals(ATTRIBUTES_JSON, subject.getAttributesJson());
        assertEquals(Collections.emptyMap(), subject.getFeatures());
        assertTrue(subject.getEnabled());
        assertFalse(subject.getIsQaMode());
        assertFalse(subject.getAllowUrlOverride());
        assertEquals("http://localhost:3000", subject.getUrl());
        assertEquals(forcedVariations, subject.getForcedVariationsMap());
        assertSame(trackingCallback, subject.getTrackingCallback());
        assertEquals(new JsonObject(), subject.getSavedGroups());
    }

    @Test
    @DisplayName("Normalizes numeric forced variations and drops non-numeric ones")
    void normalizesForcedVariations() {
        // Given
        Map<String, Object> forcedVariations = new HashMap<>();
        forcedVariations.put("integer", 1);
        forcedVariations.put("double", 1.0);
        forcedVariations.put("invalid", "abc");

        // When
        GBContext subject = GBContext.builder().forcedVariationsMap(forcedVariations).build();

        // Then
        Map<String, Integer> expected = new HashMap<>();
        expected.put("integer", 1);
        expected.put("double", 1);
        assertEquals(expected, subject.getForcedVariationsMap());
    }

    @Test
    @DisplayName("The builder keeps the given settings and parses the attributes JSON")
    void builderKeepsSettingsAndParsesAttributes() {
        // Given
        JsonObject expectedAttributes = new JsonObject();
        expectedAttributes.addProperty("country", "canada");
        expectedAttributes.addProperty("device", "android");

        // When
        GBContext subject = GBContext.builder()
                .enabled(true)
                .attributesJson(ATTRIBUTES_JSON)
                .url("http://localhost:3000")
                .featuresJson("{}")
                .forcedVariationsMap(new HashMap<String, Integer>())
                .isQaMode(false)
                .build();

        // Then
        assertEquals(expectedAttributes, subject.getAttributes());
        assertEquals(Collections.emptyMap(), subject.getFeatures());
        assertTrue(subject.getEnabled());
        assertFalse(subject.getIsQaMode());
        assertEquals("http://localhost:3000", subject.getUrl());
    }

    @Test
    @DisplayName("The setters replace the initial state")
    void settersReplaceInitialState() {
        // Given
        GBContext subject = GBContext.builder().enabled(true).isQaMode(false).url("http://localhost:3000").build();

        // When
        subject.setEnabled(false);
        subject.setIsQaMode(true);
        subject.setUrl("http://localhost:3001");

        // Then
        assertFalse(subject.getEnabled());
        assertTrue(subject.getIsQaMode());
        assertEquals("http://localhost:3001", subject.getUrl());
    }

    @Test
    @DisplayName("The constructor decrypts encrypted features with the encryption key")
    void constructorDecryptsEncryptedFeatures() {
        // When
        GBContext subject = new GBContext(ATTRIBUTES_JSON, null, ENCRYPTED_FEATURES, null, ENCRYPTION_KEY, true, false,
                "http://localhost:3000", false, new HashMap<String, Integer>(), null, null, null, null, null, null);

        // Then
        assertEquals(expectedDecryptedFeaturesJson(), GrowthBookJsonUtils.getInstance().gson.toJson(subject.getFeatures()));
    }

    @Test
    @DisplayName("The builder decrypts encrypted features with the encryption key")
    void builderDecryptsEncryptedFeatures() {
        // When
        GBContext subject = GBContext.builder()
                .attributesJson(ATTRIBUTES_JSON)
                .featuresJson(ENCRYPTED_FEATURES)
                .encryptionKey(ENCRYPTION_KEY)
                .build();

        // Then
        assertEquals(expectedDecryptedFeaturesJson(), GrowthBookJsonUtils.getInstance().gson.toJson(subject.getFeatures()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "invalid key, 7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==, nope",
            "malformed payload, foo.bar.baz.==.ow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==, BhB1wORFmZLTDjbvstvS8w=="
    })
    @DisplayName("Leaves features empty when the encrypted payload cannot be decrypted")
    void leavesFeaturesEmptyWhenDecryptionFails(String scenario, String encryptedFeatures, String encryptionKey) {
        // When
        GBContext subject = GBContext.builder()
                .attributesJson(ATTRIBUTES_JSON)
                .featuresJson(encryptedFeatures)
                .encryptionKey(encryptionKey)
                .build();

        // Then
        assertEquals(Collections.emptyMap(), subject.getFeatures());
    }

    private static String expectedDecryptedFeaturesJson() {
        return GrowthBookJsonUtils.getInstance().gson.toJson(TransformationUtil.transformFeatures(DECRYPTED_FEATURES));
    }
}
