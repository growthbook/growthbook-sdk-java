package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.CachingManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

class GBFeaturesRepositoryTest {

    @Test
    void canBeConstructed_withNullEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                null,
                null,
                null,
                null,
                true,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getFeaturesEndpoint());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
    }

    @Test
    void canBeConstructed_withEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "sdk-862b5mHcP9XPugqD",
                "BhB1wORFmZLTDjbvstvS8w==",
                null,
                null,
                null,
                true,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getDecryptionKey());
    }

    @Test
    void canBeBuilt_withNullEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getFeaturesEndpoint());
    }

    @Test
    void canBeBuilt_withEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-862b5mHcP9XPugqD")
                .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getDecryptionKey());
    }

    /*
    @Test
    void canFetchUnencryptedFeatures_real() throws FeatureFetchException {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
            .apiHost("https://cdn.growthbook.io")
            .clientKey("sdk-pGmC6LrsiUoEUcpZ")
            .build();

        subject.initialize();

        assertTrue(subject.getFeaturesJson().startsWith("{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\""));
    }
    */

    @Test
    void canFetchUnencryptedFeatures_mockedResponse() throws FeatureFetchException, IOException {
        String fakeResponseJson = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                null,
                mockOkHttpClient,
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
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
            .apiHost("https://cdn.growthbook.io")
            .clientKey("sdk-862b5mHcP9XPugqD")
            .encryptionKey(encryptionKey)
            .build();

        subject.initialize();

        String expected = "{\"greeting\":{\"defaultValue\":\"hello, this is a message from encrypted features!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"holaaaaa\"}]}}";
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
                "http://localhost:80",
                "abc-123",
                encryptionKey,
                null,
                null,
                mockOkHttpClient,
                null,
                null
        );
        subject.initialize();

        String expected = "{\"targeted_percentage_rollout\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":\"foo\"},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"test_feature\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":{\"$not\":{\"$regex\":\"foo\"},\"$eq\":\"\"}},\"force\":true}]},\"sample_json\":{\"defaultValue\":{}},\"string_feature\":{\"defaultValue\":\"hello, world!\"},\"some_test_feature\":{\"defaultValue\":true},\"my_new_feature_jan17_5\":{\"defaultValue\":true},\"my_new_feature_jan17_13\":{\"defaultValue\":true}}";
        assertEquals(expected, subject.getFeaturesJson().trim());
    }

    @Test
    void testOnFeaturesRefresh_Success() {
        String fakeResponseJson = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";

        OkHttpClient mockOkHttpClient = mock(OkHttpClient.class);

        Call mockCall = mock(Call.class);
        doReturn(mockCall).when(mockOkHttpClient).newCall(any(Request.class));

        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("").body(
                        ResponseBody.create(
                                fakeResponseJson,
                                MediaType.parse("application/json")
                        ))
                .build();

        doAnswer(invocation -> {
            Callback mockCallback = invocation.getArgument(0);
            mockCallback.onResponse(mockCall, response);
            return null;
        }).when(mockCall).enqueue(any(Callback.class));

        FeatureRefreshCallback featureRefreshCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                0,
                mockOkHttpClient,
                null,
                null
        );

        subject.onFeaturesRefresh(featureRefreshCallback);

        subject.getFeaturesJson();

        String expected = "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}";
        verify(featureRefreshCallback).onRefresh(expected);
        verify(featureRefreshCallback, never()).onError(any(Throwable.class));
    }

    @Test
    void testOnFeaturesRefresh_Error() {
        OkHttpClient mockOkHttpClient = mock(OkHttpClient.class);
        IOException requestFailed = new IOException("Request failed");

        Call mockCall = mock(Call.class);
        doReturn(mockCall).when(mockOkHttpClient).newCall(any(Request.class));
        doAnswer(invocation -> {
            Callback mockCallback = invocation.getArgument(0);
            mockCallback.onFailure(mockCall, requestFailed);
            return null;
        }).when(mockCall).enqueue(any(Callback.class));

        FeatureRefreshCallback featureRefreshCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                0,
                mockOkHttpClient,
                null,
                null
        );

        subject.onFeaturesRefresh(featureRefreshCallback);

        subject.getFeaturesJson();

        verify(featureRefreshCallback).onError(requestFailed);
        verify(featureRefreshCallback, never()).onRefresh(anyString());
    }


    @Test
    void test_fetchForRemoteEval_success() throws FeatureFetchException, IOException {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockResponseBody = mock(ResponseBody.class);
        // Arrange
        GBFeaturesRepository repository = spy(new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY,
                60,
                mockHttpClient,
                null,
                null
        ));

        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();
        String expectedResponse = "{\"features\": {}}";



        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.code()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn(expectedResponse);


        // Act
        repository.fetchForRemoteEval(requestBody);

        // Assert
        verify(mockHttpClient).newCall(any(Request.class));
        verify(mockCall).execute();
        verify(mockResponseBody).string();
        assertEquals("{}", repository.getFeaturesJson());
    }

    @Test
    void test_fetchForRemoteEval_networkError() throws IOException {
        // Arrange
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);

        GBFeaturesRepository repository = spy(new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY,
                60,
                mockHttpClient,
                null,
                null
        ));

        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();



        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Network error"));

        // Act & Assert
        assertThrows(FeatureFetchException.class, () -> repository.fetchForRemoteEval(requestBody));
    }

    @Test
    void test_fetchForRemoteEval_invalidResponse() throws IOException, FeatureFetchException {
        // Arrange
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);

        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository repository = spy(new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY,
                60,
                mockHttpClient,
                null,
                null
        ));

        repository.onFeaturesRefresh(mockCallback);

        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();

        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.code()).thenReturn(500);
        when(mockResponse.message()).thenReturn("Internal Server Error");

        // Act
        repository.fetchForRemoteEval(requestBody);

        // Assert
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(mockCallback).onError(throwableCaptor.capture());

        Throwable capturedThrowable = throwableCaptor.getValue();
        assertEquals("Response is not success, response code is:500. And message is: Internal Server Error", capturedThrowable.getMessage());
    }

    @Test
    void test_fetchForRemoteEval_requestBody() throws FeatureFetchException, IOException {
        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();
        requestBody.setAttributes(new JsonObject());
        requestBody.setUrl("");
        requestBody.setForcedFeatures(new ArrayList<>());
        requestBody.setForcedVariations(new HashMap<>());

        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockResponseBody = mock(ResponseBody.class);

        GBFeaturesRepository repository = spy(new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY,
                60,
                mockHttpClient,
                null,
                null,
                requestBody
        ));

        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.code()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn("{\"features\": {}}");

        // Act
        repository.fetchForRemoteEval(requestBody);

        // Assert
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(requestCaptor.capture());

        Request capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.body());
        assertEquals("application/json; charset=utf-8", Objects.requireNonNull(capturedRequest.body().contentType()).toString());
    }

    @Test()
    void testOnInitializeHttpError() throws IOException {
        OkHttpClient mockOkHttpClient = mock(OkHttpClient.class);

        String errorResponseJson = "{\"status\": 400, \"error\": \"Invalid API Key\"}";
        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(400).message("").body(
                        ResponseBody.create(
                                errorResponseJson,
                                MediaType.parse("application/json")
                        ))
                .build();

        Call mockCall = mock(Call.class);
        doReturn(mockCall).when(mockOkHttpClient).newCall(any(Request.class));
        doReturn(response).when(mockCall).execute();

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                0,
                mockOkHttpClient,
                null,
                null
        );

        assertThrows(
                FeatureFetchException.class,
                subject::initialize,
                "HTTP_RESPONSE_ERROR : responded with status 400"
        );

    }

    /*
    @Test
    void testUserAgentHeaders() throws FeatureFetchException {
        String endpoint = "http://localhost:3100/healthcheck";
        GBFeaturesRepository subject = new GBFeaturesRepository(endpoint, null);
        subject.initialize();
    }
    */

    @Test
    void test_getFeaturesFromCacheSuccessfully() throws FeatureFetchException, IOException {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        CachingManager mockCacheManager = mock(CachingManager.class);
        String cachedData = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
        String expectedResult = "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}";
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "abc-123",
                null,
                null,
                null,
                mockHttpClient,
                false,
                null
        );

        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Http error"));
        when(mockCacheManager.loadCache(anyString())).thenReturn(cachedData);

        subject.setCachingManager(mockCacheManager);
        subject.initialize();
        String actualResult = subject.getFeaturesJson();
        assertEquals(expectedResult, actualResult);
        verify(mockCacheManager).loadCache(anyString());
    }


    /**
     * Create a mock instance of {@link OkHttpClient}
     *
     * @param serializedBody JSON string response
     * @return mock {@link OkHttpClient}
     */
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
