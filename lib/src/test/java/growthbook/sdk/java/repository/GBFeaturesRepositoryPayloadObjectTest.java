package growthbook.sdk.java.repository;

import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the raw/decrypted payload objects surfaced by the repository, driven through
 * a mocked HTTP client so no real network is involved.
 */
class GBFeaturesRepositoryPayloadObjectTest {

    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    private static final String ENCRYPTED_FEATURES =
            "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
    private static final String DECRYPTION_KEY = "BhB1wORFmZLTDjbvstvS8w==";

    @Test
    @DisplayName("Verify: an unencrypted seeded payload is returned as-is and decrypts to itself")
    void unencryptedPayloadIsIdentity() throws FeatureFetchException {
        GBFeaturesRepository subject = repository(
                "{\"features\":{\"my-flag\":{\"defaultValue\":true}},\"savedGroups\":{}}", null);
        subject.initialize();

        // When
        JsonObject payload = subject.getPayloadObject();

        // Then
        assertTrue(payload.getAsJsonObject("features").has("my-flag"));
        assertEquals(payload, subject.getDecryptedPayloadObject());

        subject.shutdown();
    }

    @Test
    @DisplayName("Verify: an encrypted seeded payload keeps encryptedFeatures raw and decrypts on demand")
    void encryptedPayloadDecryptsOnDemand() throws FeatureFetchException {
        GBFeaturesRepository subject = repository(
                "{\"encryptedFeatures\":\"" + ENCRYPTED_FEATURES + "\"}", DECRYPTION_KEY);
        subject.initialize();

        // When
        JsonObject raw = subject.getPayloadObject();
        JsonObject decrypted = subject.getDecryptedPayloadObject();

        // Then
        assertTrue(raw.has("encryptedFeatures"));
        assertFalse(raw.has("features"));
        assertFalse(decrypted.has("encryptedFeatures"));
        assertTrue(decrypted.getAsJsonObject("features").has("greeting"));
        assertNotEquals(raw, decrypted);

        subject.shutdown();
    }

    @Test
    @DisplayName("Verify: repeated decrypted-payload reads return equal but independent copies")
    void decryptedPayloadReturnsIndependentCopies() throws FeatureFetchException {
        GBFeaturesRepository subject = repository(
                "{\"encryptedFeatures\":\"" + ENCRYPTED_FEATURES + "\"}", DECRYPTION_KEY);
        subject.initialize();

        // When
        JsonObject first = subject.getDecryptedPayloadObject();
        JsonObject second = subject.getDecryptedPayloadObject();

        // Then
        assertEquals(first, second);
        assertNotSame(first, second);
        first.addProperty("mutation", true);
        assertFalse(subject.getDecryptedPayloadObject().has("mutation"));

        subject.shutdown();
    }

    @Test
    @DisplayName("Verify: the raw payload accessor reflects the latest refresh, not the previous response")
    void rawPayloadReflectsLatestRefresh() throws FeatureFetchException {
        GBFeaturesRepository subject = refreshingRepository(
                "{\"features\":{\"flag-a\":{\"defaultValue\":true}}}",
                "{\"features\":{\"flag-b\":{\"defaultValue\":false}}}");

        // Given the first response is loaded
        subject.refreshFeatures(RefreshMode.FORCE);
        assertTrue(subject.getPayloadObject().getAsJsonObject("features").has("flag-a"));

        // When a refresh brings a different response
        subject.refreshFeatures(RefreshMode.FORCE);

        // Then the accessor exposes only the latest response
        JsonObject latest = subject.getPayloadObject();
        assertFalse(latest.getAsJsonObject("features").has("flag-a"));
        assertTrue(latest.getAsJsonObject("features").has("flag-b"));

        subject.shutdown();
    }

    @Test
    @DisplayName("Verify: the decrypted-payload memo is invalidated when a refresh changes the response")
    void decryptedPayloadMemoInvalidatesOnRefresh() throws FeatureFetchException {
        GBFeaturesRepository subject = refreshingRepository(
                "{\"features\":{\"flag-a\":{\"defaultValue\":true}}}",
                "{\"features\":{\"flag-b\":{\"defaultValue\":false}}}");

        // Given the first response is loaded and memoized by reading it once
        subject.refreshFeatures(RefreshMode.FORCE);
        assertTrue(subject.getDecryptedPayloadObject().getAsJsonObject("features").has("flag-a"));

        // When a refresh brings a different response
        subject.refreshFeatures(RefreshMode.FORCE);

        // Then the memo is rebuilt against the new response, not served stale
        JsonObject latest = subject.getDecryptedPayloadObject();
        assertFalse(latest.getAsJsonObject("features").has("flag-a"));
        assertTrue(latest.getAsJsonObject("features").has("flag-b"));

        subject.shutdown();
    }

    @Test
    @DisplayName("Verify: both payload accessors are empty before initialization")
    void payloadIsEmptyBeforeInitialize() {
        GBFeaturesRepository subject = repository(
                "{\"features\":{\"my-flag\":{\"defaultValue\":true}}}", null);

        // Then
        assertEquals(0, subject.getPayloadObject().size());
        assertEquals(0, subject.getDecryptedPayloadObject().size());
    }

    private static GBFeaturesRepository refreshingRepository(String firstResponse, String secondResponse) {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        try {
            when(call.execute()).thenReturn(jsonResponse(firstResponse), jsonResponse(secondResponse));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(okHttpClient.newCall(any())).thenReturn(call);
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .okHttpClient(okHttpClient)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .isCacheDisabled(true)
                .build();
    }

    private static Response jsonResponse(String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://localhost/features").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }

    private static GBFeaturesRepository repository(String payload, String decryptionKey) {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        try {
            // A fresh Response per call: a Response body is single-use, so a SWR background
            // refresh that re-fetches would otherwise read an already-consumed body.
            when(call.execute()).thenAnswer(invocation -> jsonResponse(payload));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(okHttpClient.newCall(any())).thenReturn(call);
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .decryptionKey(decryptionKey)
                .okHttpClient(okHttpClient)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .isCacheDisabled(true)
                .build();
    }
}
