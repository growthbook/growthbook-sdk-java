package growthbook.sdk.java;

import okhttp3.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GBFeaturesRepositoryTest {

    @Test
    void canBeConstructed_withNullEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
            null,
            null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getEndpoint());
    }

    @Test
    void canBeConstructed_withEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD",
            "BhB1wORFmZLTDjbvstvS8w==",
            null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test
    void canBeBuilt_withNullEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
            .builder()
            .endpoint("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
            .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getEndpoint());
    }

    @Test
    void canBeBuilt_withEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
            .builder()
            .endpoint("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD")
            .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
            .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    /*
    @Test
    void canFetchUnencryptedFeatures_real() throws FeatureFetchException {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
            null,
            null
        );

        subject.initialize();

        assertEquals(
            "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}",
            subject.getFeaturesJson()
        );
    }
    */

    @Test
    void canFetchUnencryptedFeatures_mockedResponse() throws FeatureFetchException, IOException {
        String fakeResponseJson = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);

        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            null,
            null
        );
        subject.initialize();

        String expected = "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}";
        assertEquals(expected, subject.getFeaturesJson());
    }

    /*
    @Test
    void canFetchEncryptedFeatures_real() throws FeatureFetchException {
        String endpoint = "https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";
        GBFeaturesRepository subject = new GBFeaturesRepository(endpoint, encryptionKey, null);

        subject.initialize();

        String expected = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"holaaaaa\"}]}}";
        String actual = subject.getFeaturesJson();
        System.out.println(actual);

        assertEquals(expected, actual.trim());
    }
    */

    @Test
    void canFetchEncryptedFeatures_mockedResponse() throws IOException, FeatureFetchException {
        String fakeResponseJson = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {},\n" +
            "  \"dateUpdated\": \"2023-01-25T00:51:26.772Z\",\n" +
            "  \"encryptedFeatures\": \"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"\n" +
            "}";
        String encryptionKey = "o0maZL/O7AphxcbRvaJIzw==";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);

        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            encryptionKey,
            null
        );
        subject.initialize();

        String expected = "{\"targeted_percentage_rollout\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":\"foo\"},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"test_feature\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":{\"$not\":{\"$regex\":\"foo\"},\"$eq\":\"\"}},\"force\":true}]},\"sample_json\":{\"defaultValue\":{}},\"string_feature\":{\"defaultValue\":\"hello, world!\"},\"some_test_feature\":{\"defaultValue\":true},\"my_new_feature_jan17_5\":{\"defaultValue\":true},\"my_new_feature_jan17_13\":{\"defaultValue\":true}}";
        assertEquals(expected, subject.getFeaturesJson().trim());
    }

    @Test
    void testOnFeaturesRefresh_Success() throws IOException, FeatureFetchException {
        String fakeResponseJson = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);
        FeatureRefreshCallback featureRefreshCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                mockOkHttpClient,
                "http://localhost:80",
                null,
                0
        );
        subject.initialize();

        subject.onFeaturesRefresh(featureRefreshCallback);

        subject.getFeaturesJson();

        String expected = "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}";
        verify(featureRefreshCallback).onRefresh(expected);
        verify(featureRefreshCallback, never()).onError(any(Throwable.class));
    }

    @Test
    void testOnFeaturesRefresh_Error() throws IOException, FeatureFetchException {
        String fakeResponseJson = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
        IOException error = new IOException("Request failed");
        OkHttpClient mockOkHttpClient = mockHttpClientFailedCallback(fakeResponseJson, error);
        FeatureRefreshCallback featureRefreshCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                mockOkHttpClient,
                "http://localhost:80",
                null,
                0
        );
        subject.initialize();

        subject.onFeaturesRefresh(featureRefreshCallback);

        subject.getFeaturesJson();

        verify(featureRefreshCallback).onError(error);
        verify(featureRefreshCallback, never()).onRefresh(anyString());
    }

    /*
    @Test
    void testUserAgentHeaders() throws FeatureFetchException {
        String endpoint = "http://localhost:3100/healthcheck";
        GBFeaturesRepository subject = new GBFeaturesRepository(endpoint, null);
        subject.initialize();
    }
    */

    /**
     * Create a mock instance of {@link OkHttpClient}
     * @param serializedBody JSON string response
     * @return mock {@link OkHttpClient}
     */
    private static OkHttpClient mockHttpClient(final String serializedBody) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);

        Call remoteCall = mock(Call.class);

        when(remoteCall.execute()).thenReturn(getResponse(serializedBody));
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

        //required for original callback okHttpClient
        doAnswer(invocation -> {
            ((Callback) invocation.getArgument(0)).onResponse(remoteCall, getResponse(serializedBody));
            return null;
        }).when(remoteCall).enqueue(any(Callback.class));

        return okHttpClient;
    }

    /**
     * Create a mock instance for failed callback of {@link OkHttpClient}
     *
     * @param serializedBody JSON string response
     * @return mock {@link OkHttpClient}
     */
    private static OkHttpClient mockHttpClientFailedCallback(final String serializedBody, final IOException ioException) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call remoteCall = mock(Call.class);
        when(remoteCall.execute()).thenReturn(getResponse(serializedBody));
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

        doAnswer(invocation -> {
            Callback passedCallback = invocation.getArgument(0);
            passedCallback.onFailure(remoteCall, ioException);
            return null;
        }).when(remoteCall).enqueue(any(Callback.class));

        return okHttpClient;
    }

    /**
     * Create new {@link Response}
     *
     * @param serializedBody JSON string response
     * @return mock {@link Response}
     */
    private static Response getResponse(final String serializedBody) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("").body(
                        ResponseBody.create(
                                serializedBody,
                                MediaType.parse("application/json")
                        ))
                .build();
    }
}
