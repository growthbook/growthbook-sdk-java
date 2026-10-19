package growthbook.sdk.java.util;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadUtilsTest {

    private static final String ENCRYPTED_FEATURES =
            "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
    private static final String DECRYPTION_KEY = "BhB1wORFmZLTDjbvstvS8w==";

    @Test
    @DisplayName("Verify: a null or blank raw response parses to an empty payload")
    void parsePayloadReturnsEmptyForNullOrBlank() {
        // When & Then
        assertEquals(new JsonObject(), PayloadUtils.parsePayload(null));
        assertEquals(new JsonObject(), PayloadUtils.parsePayload("   "));
    }

    @Test
    @DisplayName("Verify: a raw response that is not a JSON object parses to an empty payload")
    void parsePayloadReturnsEmptyForNonObject() {
        // When & Then
        assertEquals(new JsonObject(), PayloadUtils.parsePayload("[1,2,3]"));
        assertEquals(new JsonObject(), PayloadUtils.parsePayload("42"));
        assertEquals(new JsonObject(), PayloadUtils.parsePayload("{not valid json"));
    }

    @Test
    @DisplayName("Verify: a valid raw response parses to the same JSON object")
    void parsePayloadReturnsParsedObject() {
        // Given
        String raw = "{\"features\":{\"a\":{\"defaultValue\":true}},\"savedGroups\":{\"g\":[1]}}";

        // When
        JsonObject payload = PayloadUtils.parsePayload(raw);

        // Then
        assertTrue(payload.has("features"));
        assertTrue(payload.getAsJsonObject("features").has("a"));
        assertTrue(payload.has("savedGroups"));
    }

    @Test
    @DisplayName("Verify: reconstruct emits an empty features object when features are null")
    void reconstructPayloadEmitsEmptyFeaturesForNull() {
        // When
        JsonObject payload = PayloadUtils.reconstructPayload(null, null, null);

        // Then
        assertTrue(payload.has("features"));
        assertEquals(0, payload.getAsJsonObject("features").size());
        assertFalse(payload.has("savedGroups"));
        assertFalse(payload.has("contextualBandits"));
    }

    @Test
    @DisplayName("Verify: reconstruct includes features and non-empty saved groups and bandits")
    void reconstructPayloadIncludesNonEmptySections() {
        // Given
        Map<String, Feature<?>> features = new HashMap<>();
        features.put("a", new Feature<>());
        JsonObject savedGroups = new JsonObject();
        savedGroups.addProperty("g", "v");
        JsonObject bandits = new JsonObject();
        bandits.addProperty("b", "v");

        // When
        JsonObject payload = PayloadUtils.reconstructPayload(features, savedGroups, bandits);

        // Then
        assertTrue(payload.getAsJsonObject("features").has("a"));
        assertTrue(payload.has("savedGroups"));
        assertTrue(payload.has("contextualBandits"));
    }

    @Test
    @DisplayName("Verify: reconstruct omits empty saved groups and bandits")
    void reconstructPayloadOmitsEmptySections() {
        // When
        JsonObject payload = PayloadUtils.reconstructPayload(new HashMap<>(), new JsonObject(), new JsonObject());

        // Then
        assertFalse(payload.has("savedGroups"));
        assertFalse(payload.has("contextualBandits"));
    }

    @Test
    @DisplayName("Verify: decrypt returns an equal copy when there is no decryption key")
    void decryptPayloadIsIdentityWithoutKey() {
        // Given
        JsonObject raw = PayloadUtils.parsePayload("{\"features\":{\"a\":{\"defaultValue\":true}}}");

        // When
        JsonObject decrypted = PayloadUtils.decryptPayload(raw, null);

        // Then
        assertEquals(raw, decrypted);
    }

    @Test
    @DisplayName("Verify: decrypt returns an equal copy for an unencrypted payload even when a key is present")
    void decryptPayloadIsIdentityForUnencryptedPayload() {
        // Given
        JsonObject raw = PayloadUtils.parsePayload("{\"features\":{\"a\":{\"defaultValue\":true}},\"savedGroups\":{}}");

        // When
        JsonObject decrypted = PayloadUtils.decryptPayload(raw, DECRYPTION_KEY);

        // Then
        assertEquals(raw, decrypted);
    }

    @Test
    @DisplayName("Verify: decrypt replaces encryptedFeatures with plaintext features")
    void decryptPayloadResolvesEncryptedFeatures() {
        // Given
        JsonObject raw = new JsonObject();
        raw.addProperty("encryptedFeatures", ENCRYPTED_FEATURES);

        // When
        JsonObject decrypted = PayloadUtils.decryptPayload(raw, DECRYPTION_KEY);

        // Then
        assertFalse(decrypted.has("encryptedFeatures"));
        assertTrue(decrypted.getAsJsonObject("features").has("greeting"));
    }

    @Test
    @DisplayName("Verify: decrypt leaves an undecryptable encrypted field intact")
    void decryptPayloadLeavesUndecryptableFieldIntact() {
        // Given
        JsonObject raw = new JsonObject();
        raw.addProperty("encryptedFeatures", ENCRYPTED_FEATURES);

        // When
        JsonObject decrypted = PayloadUtils.decryptPayload(raw, "wrong-key");

        // Then
        assertTrue(decrypted.has("encryptedFeatures"));
        assertFalse(decrypted.has("features"));
    }

    @Test
    @DisplayName("Verify: extractExperiments returns empty for null or an absent experiments array")
    void extractExperimentsReturnsEmptyWhenAbsent() {
        // When & Then
        assertTrue(PayloadUtils.extractExperiments(null).isEmpty());
        assertTrue(PayloadUtils.extractExperiments(new JsonObject()).isEmpty());
        assertTrue(PayloadUtils.extractExperiments(
                PayloadUtils.parsePayload("{\"experiments\":\"notAnArray\"}")).isEmpty());
    }

    @Test
    @DisplayName("Verify: extractExperiments parses the experiments array into Experiment objects")
    void extractExperimentsParsesArray() {
        // Given
        JsonObject payload = PayloadUtils.parsePayload(
                "{\"experiments\":[{\"key\":\"exp1\",\"variations\":[0,1]},{\"key\":\"exp2\",\"variations\":[0,1]}]}");

        // When
        List<Experiment<?>> experiments = PayloadUtils.extractExperiments(payload);

        // Then
        assertEquals(2, experiments.size());
        assertEquals("exp1", experiments.get(0).getKey());
        assertEquals("exp2", experiments.get(1).getKey());
    }

    @Test
    @DisplayName("Verify: the list returned by extractExperiments is unmodifiable")
    void extractExperimentsReturnsUnmodifiableList() {
        // Given
        List<Experiment<?>> experiments = PayloadUtils.extractExperiments(
                PayloadUtils.parsePayload("{\"experiments\":[{\"key\":\"exp1\"}]}"));

        // When & Then
        assertThrows(UnsupportedOperationException.class, () -> experiments.add(null));
    }
}
