package growthbook.sdk.java.multiusermode.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TransformationUtilTest {

    @Test
    @DisplayName("Parses a features JSON object into features by key")
    void parsesFeaturesJsonIntoFeaturesByKey() {
        // When
        Map<String, Feature<?>> features = TransformationUtil.transformFeatures(
                "{\"dark-mode\":{\"defaultValue\":true},\"banner\":{\"defaultValue\":\"blue\"}}");

        // Then
        assertEquals(2, features.size());
        assertEquals(true, features.get("dark-mode").getDefaultValue());
        assertEquals("blue", features.get("banner").getDefaultValue());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"{\"dark-mode\":", "[1,2]", "{\"dark-mode\":{\"rules\":[1]}}"})
    @DisplayName("Gives no features for a missing or unparsable features JSON")
    void givesNoFeaturesForMissingOrUnparsableJson(String json) {
        // When
        Map<String, Feature<?>> features = TransformationUtil.transformFeatures(json);

        // Then
        assertTrue(features.isEmpty());
    }

    @Test
    @DisplayName("Parses a saved groups JSON object")
    void parsesSavedGroupsJson() {
        // When
        JsonObject savedGroups = TransformationUtil.transformSavedGroups("{\"beta-users\":[\"user-1\",\"user-2\"]}");

        // Then
        assertEquals(json("{\"beta-users\":[\"user-1\",\"user-2\"]}"), savedGroups);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"[1,2]", "{\"beta-users\":"})
    @DisplayName("Gives empty saved groups for a missing or unparsable saved groups JSON")
    void givesEmptySavedGroupsForMissingOrUnparsableJson(String json) {
        // When
        JsonObject savedGroups = TransformationUtil.transformSavedGroups(json);

        // Then
        assertEquals(new JsonObject(), savedGroups);
    }

    @Test
    @DisplayName("Parses plain features JSON when no encryption key is given")
    void parsesPlainFeaturesWithoutEncryptionKey() {
        // When
        Map<String, Feature<?>> features = TransformationUtil.transformEncryptedFeatures(
                "{\"dark-mode\":{\"defaultValue\":false}}", null);

        // Then
        assertEquals(1, features.size());
        assertEquals(false, features.get("dark-mode").getDefaultValue());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {"not-an-encrypted-payload"})
    @DisplayName("Gives no features when an encrypted payload is missing or cannot be decrypted")
    void givesNoFeaturesForMissingOrUndecryptablePayload(String payload) {
        // When
        Map<String, Feature<?>> features = TransformationUtil.transformEncryptedFeatures(payload, "Zvwv/+uhpFDznZ6SX28Yjg==");

        // Then
        assertTrue(features.isEmpty());
    }

    @Test
    @DisplayName("Parses a JSON object of attributes")
    void parsesAttributesJson() {
        // When
        JsonObject attributes = TransformationUtil.transformAttributes("{\"id\":\"user-1\",\"premium\":true}");

        // Then
        assertEquals(json("{\"id\":\"user-1\",\"premium\":true}"), attributes);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"[\"user-1\"]", "\"user-1\"", "{\"id\":"})
    @DisplayName("Gives empty attributes for missing, non-object or unparsable attributes JSON")
    void givesEmptyAttributesForMissingNonObjectOrUnparsableJson(String json) {
        // When
        JsonObject attributes = TransformationUtil.transformAttributes(json);

        // Then
        assertEquals(new JsonObject(), attributes);
    }

    private static JsonObject json(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonObject.class);
    }
}
