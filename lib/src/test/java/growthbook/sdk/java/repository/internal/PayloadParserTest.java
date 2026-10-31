package growthbook.sdk.java.repository.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PayloadParserTest {

    private static final String FEATURES = "{\"flag-a\":{\"defaultValue\":true}}";
    private static final String SAVED_GROUPS = "{\"grp_a\":[\"id-1\",\"id-2\"]}";
    private static final String ENCRYPTION_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String WRONG_KEY = Base64.getEncoder()
            .encodeToString("fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("Extracts features and saved groups from a plain payload")
    void extractsFeaturesAndSavedGroupsFromPlainPayload() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(null);
        String body = "{\"features\":" + FEATURES + ",\"savedGroups\":" + SAVED_GROUPS + "}";

        // When
        PayloadParser.Payload payload = parser.parse(body);

        // Then
        assertEquals(FEATURES, payload.features());
        assertEquals(SAVED_GROUPS, payload.savedGroups());
    }

    @Test
    @DisplayName("Returns empty saved groups when a plain payload has none")
    void returnsEmptySavedGroupsWhenAbsent() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(null);

        // When
        PayloadParser.Payload payload = parser.parse("{\"features\":" + FEATURES + "}");

        // Then
        assertEquals(FEATURES, payload.features());
        assertEquals("", payload.savedGroups());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"grp\"", "[1,2]", "42"})
    @DisplayName("Returns empty saved groups when saved groups are not an object")
    void returnsEmptySavedGroupsWhenNotAnObject(String savedGroups) throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(null);

        // When
        PayloadParser.Payload payload = parser.parse("{\"features\":" + FEATURES + ",\"savedGroups\":" + savedGroups + "}");

        // Then
        assertEquals(FEATURES, payload.features());
        assertEquals("", payload.savedGroups());
    }

    @Test
    @DisplayName("Accepts an empty features object")
    void acceptsEmptyFeaturesObject() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(null);

        // When
        PayloadParser.Payload payload = parser.parse("{\"features\":{}}");

        // Then
        assertEquals("{}", payload.features());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "[{\"features\":{}}]",
            "<html><body>Bad Gateway</body></html>",
            "{\"features\":{\"flag-a\":",
            "{}",
            "{\"features\":null}",
            "{\"features\":\"x\"}",
            "{\"features\":[]}",
            "plain text body"
    })
    @DisplayName("Rejects an unusable plain body as a configuration error")
    void rejectsUnusablePlainBody(String body) {
        // Given
        PayloadParser parser = new PayloadParser(null);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> parser.parse(body));

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("Decrypts encrypted features and saved groups")
    void decryptsEncryptedFeaturesAndSavedGroups() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(ENCRYPTION_KEY);
        String body = "{\"encryptedFeatures\":\"" + encrypt(FEATURES)
                + "\",\"encryptedSavedGroups\":\"" + encrypt(SAVED_GROUPS) + "\"}";

        // When
        PayloadParser.Payload payload = parser.parse(body);

        // Then
        assertEquals(FEATURES, payload.features());
        assertEquals(SAVED_GROUPS, payload.savedGroups());
    }

    @Test
    @DisplayName("Returns empty saved groups when an encrypted payload has none")
    void returnsEmptySavedGroupsWhenEncryptedPayloadHasNone() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(ENCRYPTION_KEY);

        // When
        PayloadParser.Payload payload = parser.parse("{\"encryptedFeatures\":\"" + encrypt(FEATURES) + "\"}");

        // Then
        assertEquals(FEATURES, payload.features());
        assertEquals("", payload.savedGroups());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"features\":" + FEATURES + "}",
            "{\"encryptedFeatures\":null}",
            "{\"encryptedFeatures\":42}",
            "{\"encryptedFeatures\":{}}",
            "{\"encryptedFeatures\":[\"a.b\"]}"
    })
    @DisplayName("Rejects an encrypted body without encrypted features as a configuration error")
    void rejectsEncryptedBodyWithoutEncryptedFeatures(String body) {
        // Given
        PayloadParser parser = new PayloadParser(ENCRYPTION_KEY);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> parser.parse(body));

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[1,2]", "\"text\"", "42", "plain text"})
    @DisplayName("Rejects encrypted features that do not decrypt to a JSON object as a configuration error")
    void rejectsEncryptedFeaturesThatAreNotAnObject(String plainText) throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(ENCRYPTION_KEY);
        String body = "{\"encryptedFeatures\":\"" + encrypt(plainText) + "\"}";

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> parser.parse(body));

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("Reports a decryption failure with a wrong key as unknown")
    void reportsWrongKeyAsUnknown() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(WRONG_KEY);
        String body = "{\"encryptedFeatures\":\"" + encrypt(FEATURES) + "\"}";

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> parser.parse(body));

        // Then
        assertEquals(FeatureFetchErrorCode.UNKNOWN, exception.getErrorCode());
    }

    @Test
    @DisplayName("Reports a malformed ciphertext as unknown")
    void reportsMalformedCiphertextAsUnknown() {
        // Given
        PayloadParser parser = new PayloadParser(ENCRYPTION_KEY);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class,
                () -> parser.parse("{\"encryptedFeatures\":\"no-separator\"}"));

        // Then
        assertEquals(FeatureFetchErrorCode.UNKNOWN, exception.getErrorCode());
    }

    @Test
    @DisplayName("Reports undecryptable saved groups as unknown")
    void reportsUndecryptableSavedGroupsAsUnknown() throws Exception {
        // Given
        PayloadParser parser = new PayloadParser(ENCRYPTION_KEY);
        String body = "{\"encryptedFeatures\":\"" + encrypt(FEATURES) + "\",\"encryptedSavedGroups\":\"no-separator\"}";

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> parser.parse(body));

        // Then
        assertEquals(FeatureFetchErrorCode.UNKNOWN, exception.getErrorCode());
    }

    private static String encrypt(String plainText) throws Exception {
        byte[] iv = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(ENCRYPTION_KEY), "AES"),
                new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipherText);
    }
}
