package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
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

/**
 * Repository-layer coverage for contextual bandit payload handling: the unencrypted
 * {@code contextualBandits} key, the absent-key default, and the encrypted
 * {@code encryptedContextualBandits} key decrypted with the configured key.
 *
 * <p>Reuses the mocked {@link OkHttpClient} harness and nine-argument constructor used by
 * {@code GBFeaturesRepositoryMainBehaviorTest}; no real network and no hand-rolled crypto.
 */
class GBFeaturesRepositoryContextualBanditTest {

    @Test
    @DisplayName("Verify: unencrypted contextualBandits payload is exposed as raw JSON and parsed object")
    void unencryptedContextualBandits_areExposedRawAndParsed() throws IOException, FeatureFetchException {
        // Given
        String responseJson = "{\"features\":{\"banner_text\":{\"defaultValue\":\"hello\"}},"
                + "\"contextualBandits\":{\"bandit-1\":{\"banditVersion\":3,"
                + "\"contexts\":[{\"key\":\"country\",\"values\":[\"us\"]}]}}}";
        OkHttpClient mockOkHttpClient = mockHttpClient(responseJson);
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                null,
                mockOkHttpClient,
                true,
                null,
                null
        );

        // When
        subject.initialize();

        // Then
        assertFalse(subject.getContextualBanditsJson().isEmpty());
        assertTrue(subject.getContextualBanditsJson().contains("bandit-1"));
        JsonObject parsed = subject.getFeatureSnapshot().getParsedContextualBandits();
        assertTrue(parsed.has("bandit-1"));
        assertEquals(3, parsed.getAsJsonObject("bandit-1").get("banditVersion").getAsInt());
    }

    @Test
    @DisplayName("Verify: a payload without contextualBandits yields empty raw JSON and an empty parsed object")
    void absentContextualBandits_defaultToEmpty() throws IOException, FeatureFetchException {
        // Given
        String responseJson = "{\"features\":{\"banner_text\":{\"defaultValue\":\"hello\"}}}";
        OkHttpClient mockOkHttpClient = mockHttpClient(responseJson);
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                null,
                mockOkHttpClient,
                true,
                null,
                null
        );

        // When
        subject.initialize();

        // Then
        assertEquals("", subject.getContextualBanditsJson());
        JsonObject parsed = subject.getFeatureSnapshot().getParsedContextualBandits();
        assertEquals(0, parsed.size());
    }

    @Test
    @DisplayName("Verify: encryptedContextualBandits are decrypted into the raw JSON and parsed object")
    void encryptedContextualBandits_areDecryptedAndParsed() throws IOException, FeatureFetchException {
        // Given
        String encryptedFeatures = "jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVy"
                + "Vo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Le"
                + "my9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6"
                + "SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7a"
                + "SXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Q"
                + "e62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3m"
                + "m9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOL"
                + "tIcpuj+gNpc4pR4tqF2IOwET";
        String encryptionKey = "o0maZL/O7AphxcbRvaJIzw==";
        String responseJson = "{\"features\":{},"
                + "\"encryptedFeatures\":\"" + encryptedFeatures + "\","
                + "\"encryptedContextualBandits\":\"" + encryptedFeatures + "\"}";
        OkHttpClient mockOkHttpClient = mockHttpClient(responseJson);
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                encryptionKey,
                null,
                null,
                mockOkHttpClient,
                true,
                null,
                null
        );

        // When
        subject.initialize();

        // Then
        assertFalse(subject.getContextualBanditsJson().isEmpty());
        assertTrue(subject.getContextualBanditsJson().contains("targeted_percentage_rollout"));
        JsonObject parsed = subject.getFeatureSnapshot().getParsedContextualBandits();
        assertTrue(parsed.has("targeted_percentage_rollout"));
    }

    private static OkHttpClient mockHttpClient(final String serializedBody) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call remoteCall = mock(Call.class);
        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("").body(
                        ResponseBody.create(
                                serializedBody,
                                MediaType.parse("application/json")
                        ))
                .build();
        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);
        return okHttpClient;
    }
}
