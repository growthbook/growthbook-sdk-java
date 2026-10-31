package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How {@link GBFeaturesRepository} applies a features response body.
 */
class GBFeaturesRepositoryOnResponseJsonTest {

    private static final String ENCRYPTION_KEY = "BhB1wORFmZLTDjbvstvS8w==";

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
            "",
            "   ",
            "null",
            "[]",
            "<html>502</html>",
            "{\"features\":{\"a\":",
            "{}",
            "{\"features\":null}",
            "{\"features\":\"nope\"}",
    })
    @DisplayName("Rejects an unusable unencrypted body as a configuration error")
    void rejectsUnusableUnencryptedBody(String body) {
        // Given
        GBFeaturesRepository subject = repository(null);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class,
                () -> RepositoryInternals.applyResponse(subject, body, false));

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals("{}", subject.getFeaturesJson());
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
            "null",
            "[]",
            "{}",
            "{\"encryptedFeatures\":null}",
            "{\"encryptedFeatures\":123}",
    })
    @DisplayName("Rejects an unusable encrypted body as a configuration error")
    void rejectsUnusableEncryptedBody(String body) {
        // Given
        GBFeaturesRepository subject = repository(ENCRYPTION_KEY);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class,
                () -> RepositoryInternals.applyResponse(subject, body, false));

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals("{}", subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Applies the features object of a valid unencrypted body")
    void appliesValidUnencryptedBody() throws Exception {
        // Given
        GBFeaturesRepository subject = repository(null);

        // When
        boolean changed = RepositoryInternals.applyResponse(subject, "{\"features\":{\"flag\":{\"defaultValue\":1}}}", false);

        // Then
        assertTrue(changed);
        assertEquals("{\"flag\":{\"defaultValue\":1}}", subject.getFeaturesJson().trim());
    }

    @Test
    @DisplayName("Caches a valid body but never an unusable one")
    void cachesValidBodyButNeverUnusableOne() throws Exception {
        // Given
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-123")
                .isCacheDisabled(false)
                .cacheManager(cacheManager)
                .build();
        assertThrows(FeatureFetchException.class, () -> RepositoryInternals.applyResponse(subject, "null", false));
        int savesAfterUnusableBody = cacheManager.saveCount();

        // When
        RepositoryInternals.applyResponse(subject, "{\"features\":{}}", false);

        // Then
        assertEquals(0, savesAfterUnusableBody);
        assertEquals(1, cacheManager.saveCount());
        assertEquals("{\"features\":{}}", cacheManager.store.values().iterator().next());
    }

    private static GBFeaturesRepository repository(String decryptionKey) {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-123")
                .decryptionKey(decryptionKey)
                .isCacheDisabled(true)
                .build();
    }
}
