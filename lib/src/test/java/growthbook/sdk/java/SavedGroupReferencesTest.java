package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Payloads from an SDK Connection that passes saved groups by reference ({@code savedGroupReferences}):
 * rules use {@code $inGroup} / {@code $notInGroup} and the values arrive in a separate {@code savedGroups} map.
 */
class SavedGroupReferencesTest {

    private static final String FEATURES = "{"
            + "\"beta\":{\"defaultValue\":\"off\",\"rules\":[{\"condition\":{\"id\":{\"$inGroup\":\"grp_beta\"}},\"force\":\"on\"}]},"
            + "\"not-beta\":{\"defaultValue\":\"off\",\"rules\":[{\"condition\":{\"id\":{\"$notInGroup\":\"grp_beta\"}},\"force\":\"on\"}]}"
            + "}";
    private static final String SAVED_GROUPS = "{\"grp_beta\":[\"u_1\",\"u_2\"]}";
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @ParameterizedTest(name = "encrypted = {0}")
    @ValueSource(booleans = {false, true})
    void repositoryPayload_resolvesSavedGroupReferences(boolean encrypted) throws Exception {
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-123")
                .decryptionKey(encrypted ? ENCRYPTION_KEY : null)
                .isCacheDisabled(true)
                .build();
        String payload = encrypted
                ? "{\"encryptedFeatures\":\"" + encrypt(FEATURES) + "\",\"encryptedSavedGroups\":\"" + encrypt(SAVED_GROUPS) + "\"}"
                : "{\"features\":" + FEATURES + ",\"savedGroups\":" + SAVED_GROUPS + "}";
        Method onResponseJson = GBFeaturesRepository.class.getDeclaredMethod("onResponseJson", String.class, boolean.class);
        onResponseJson.setAccessible(true);
        onResponseJson.invoke(repository, payload, false);

        for (String id : new String[]{"u_1", "u_3"}) {
            GrowthBook growthBook = new GrowthBook(GBContext.builder()
                    .featureSnapshot(repository.getFeatureSnapshot())
                    .attributesJson("{\"id\":\"" + id + "\"}")
                    .build());

            boolean member = id.equals("u_1");
            assertEquals(member ? "on" : "off", growthBook.getFeatureValue("beta", "missing"), id);
            assertEquals(member ? "off" : "on", growthBook.getFeatureValue("not-beta", "missing"), id);
        }
    }

    @Test
    void savedGroupsJson_parsesValidJsonAndIgnoresInvalidJson() {
        assertEquals(1, GBContext.builder().savedGroupsJson(SAVED_GROUPS).build().getSavedGroups().size());
        assertEquals(0, GBContext.builder().savedGroupsJson("not json").build().getSavedGroups().size());
    }

    private static String encrypt(String plainText) throws Exception {
        byte[] iv = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(ENCRYPTION_KEY), "AES"), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipherText);
    }
}
