package growthbook.sdk.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RepositoryInternals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Payloads that pass saved groups by reference: rules use {@code $inGroup} / {@code $notInGroup} and the
 * group members arrive in a separate {@code savedGroups} map.
 */
class GrowthBookSavedGroupReferencesTest {

    private static final String FEATURES = "{"
            + "\"beta\":{\"defaultValue\":\"off\",\"rules\":[{\"condition\":{\"id\":{\"$inGroup\":\"grp_beta\"}},\"force\":\"on\"}]},"
            + "\"not-beta\":{\"defaultValue\":\"off\",\"rules\":[{\"condition\":{\"id\":{\"$notInGroup\":\"grp_beta\"}},\"force\":\"on\"}]}"
            + "}";
    private static final String SAVED_GROUPS = "{\"grp_beta\":[\"user-1\",\"user-2\"]}";
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @ParameterizedTest(name = "encrypted = {0}, id = {1}")
    @CsvSource({
            "false, user-1, on, off",
            "false, user-3, off, on",
            "true, user-1, on, off",
            "true, user-3, off, on"
    })
    @DisplayName("Resolves saved group references from a repository payload")
    void resolvesSavedGroupReferencesFromRepositoryPayload(boolean encrypted, String id, String expectedBeta,
                                                           String expectedNotBeta) throws Exception {
        // Given
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-key-1")
                .decryptionKey(encrypted ? ENCRYPTION_KEY : null)
                .isCacheDisabled(true)
                .build();
        String payload = encrypted
                ? "{\"encryptedFeatures\":\"" + encrypt(FEATURES) + "\",\"encryptedSavedGroups\":\"" + encrypt(SAVED_GROUPS) + "\"}"
                : "{\"features\":" + FEATURES + ",\"savedGroups\":" + SAVED_GROUPS + "}";
        RepositoryInternals.applyResponse(repository, payload, false);
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .featureSnapshot(repository.getFeatureSnapshot())
                .attributesJson("{\"id\":\"" + id + "\"}")
                .build());

        // When
        String beta = growthBook.getFeatureValue("beta", "missing");
        String notBeta = growthBook.getFeatureValue("not-beta", "missing");

        // Then
        assertEquals(expectedBeta, beta);
        assertEquals(expectedNotBeta, notBeta);
    }

    @Test
    @DisplayName("Parses saved groups from valid JSON")
    void parsesSavedGroupsFromValidJson() {
        // Given
        JsonArray members = new JsonArray();
        members.add("user-1");
        members.add("user-2");
        JsonObject expected = new JsonObject();
        expected.add("grp_beta", members);

        // When
        GBContext context = GBContext.builder().savedGroupsJson(SAVED_GROUPS).build();

        // Then
        assertEquals(expected, context.getSavedGroups());
    }

    @Test
    @DisplayName("Ignores saved groups given as invalid JSON")
    void ignoresSavedGroupsGivenAsInvalidJson() {
        // When
        GBContext context = GBContext.builder().savedGroupsJson("not json").build();

        // Then
        assertEquals(new JsonObject(), context.getSavedGroups());
    }

    private static String encrypt(String plainText) throws Exception {
        byte[] iv = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(ENCRYPTION_KEY), "AES"), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipherText);
    }
}
