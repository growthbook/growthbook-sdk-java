package growthbook.sdk.java;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Focused tests for {@link GBFeaturesRepository}'s response parsing. Bad response bodies must
 * surface as the SDK's declared {@link FeatureFetchException} (CONFIGURATION_ERROR) rather than
 * an unchecked exception escaping initialize()/fetchFeatures(), and must not overwrite a good
 * cached payload.
 */
class GBFeaturesRepositoryOnResponseJsonTest {

    private static final Method ON_RESPONSE_JSON = onResponseJsonMethod();

    private static Method onResponseJsonMethod() {
        try {
            Method method = GBFeaturesRepository.class
                    .getDeclaredMethod("onResponseJson", String.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GBFeaturesRepository repository(String decryptionKey) {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-123")
                .decryptionKey(decryptionKey)
                .isCacheDisabled(true)
                .build();
    }

    private static void invokeOnResponseJson(GBFeaturesRepository subject, String body) throws Throwable {
        try {
            ON_RESPONSE_JSON.invoke(subject, body, false);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void assertConfigurationError(GBFeaturesRepository subject, String body) {
        Throwable thrown = null;
        try {
            invokeOnResponseJson(subject, body);
        } catch (Throwable t) {
            thrown = t;
        }
        FeatureFetchException e = assertInstanceOf(FeatureFetchException.class, thrown,
                "Expected FeatureFetchException for body: " + body);
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR, e.getErrorCode());
    }

    @ParameterizedTest(name = "unencrypted body [{0}] -> FeatureFetchException")
    @ValueSource(strings = {
            "",                        // empty body
            "   ",                     // whitespace-only
            "null",                    // literal null
            "[]",                      // JSON array, not an object
            "<html>502</html>",        // HTML error page
            "{\"features\":{\"a\":",   // truncated / malformed JSON
            "{}",                      // no features key
            "{\"features\":null}",     // features present but JSON null
            "{\"features\":\"nope\"}", // features present but not an object
    })
    void onResponseJson_unencrypted_badBody_throwsFeatureFetchException(String body) {
        assertConfigurationError(repository(null), body);
    }

    @ParameterizedTest(name = "encrypted body [{0}] -> FeatureFetchException")
    @ValueSource(strings = {
            "null",
            "[]",
            "{}",                              // no encryptedFeatures key
            "{\"encryptedFeatures\":null}",    // present but JSON null (would NPE-style throw on getAsString)
            "{\"encryptedFeatures\":123}",     // present but not a string
    })
    void onResponseJson_encrypted_badBody_throwsFeatureFetchException(String body) {
        assertConfigurationError(repository("BhB1wORFmZLTDjbvstvS8w=="), body);
    }

    @Test
    void onResponseJson_unencrypted_validBody_setsFeaturesJson() throws Throwable {
        GBFeaturesRepository subject = repository(null);

        invokeOnResponseJson(subject, "{\"features\":{}}");

        assertEquals("{}", subject.getFeaturesJson().trim());
    }

    @Test
    void onResponseJson_badBody_doesNotOverwriteCache() throws Throwable {
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-123")
                .isCacheDisabled(false)
                .cacheManager(cacheManager)
                .build();

        // A bad body must never reach the cache and clobber a previously good payload.
        try {
            invokeOnResponseJson(subject, "null");
        } catch (FeatureFetchException ignored) {
            // expected
        }
        verify(cacheManager, never()).saveContent(anyString(), anyString());

        // A good body is cached as before.
        invokeOnResponseJson(subject, "{\"features\":{}}");
        verify(cacheManager).saveContent(anyString(), eq("{\"features\":{}}"));
    }
}
