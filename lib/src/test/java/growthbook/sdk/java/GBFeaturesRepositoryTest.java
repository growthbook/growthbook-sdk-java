package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

class GBFeaturesRepositoryTest {
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

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
                null,
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
                null,
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

    @Test
    void pollingDoesNotThrowAndDoesNotOverlap() throws Exception {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-123")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .build();

        // initialize creates http client but will fail network; we just want to ensure no exceptions in poll
        try {
            subject.initialize();
        } catch (Exception ignored) {}

        // invoke internal poll method via reflection to ensure no overlap protection throws
        java.lang.reflect.Method m = GBFeaturesRepository.class.getDeclaredMethod("pollOnceSafe");
        m.setAccessible(true);
        m.invoke(subject); // first call
        m.invoke(subject); // second immediate call should be ignored by AtomicBoolean guard

        // shutdown should not throw
        subject.shutdown();
    }

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
                true,
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
                true,
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
        try {
            when(mockCall.execute()).thenReturn(response);
        } catch (IOException e) {
            // ignore in test
        }

        FeatureRefreshCallback featureRefreshCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                0,
                mockOkHttpClient,
                null,
                null,
                null,
                null,
                null,
                NO_DELAY_RETRY_POLICY
        );

        subject.onFeaturesRefresh(featureRefreshCallback);

        // trigger initialize to perform initial fetch which will call callbacks
        try { subject.initialize(); } catch (Exception ignored) {}

        String expected = "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}";
        verify(featureRefreshCallback).onRefresh(expected);
        verify(featureRefreshCallback, never()).onError(any(Throwable.class));
    }

    @Test
    void testOnFeaturesRefresh_ContinuesAfterCallbackFailure() throws IOException, FeatureFetchException {
        String responseJson = "{\"features\":{\"test-feature\":{\"defaultValue\":true}}}";
        FeatureRefreshCallback failingCallback = mock(FeatureRefreshCallback.class);
        FeatureRefreshCallback successfulCallback = mock(FeatureRefreshCallback.class);
        doThrow(new RuntimeException("callback failed")).when(failingCallback).onRefresh(anyString());

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                60,
                mockHttpClient(responseJson),
                null,
                null,
                null,
                null
        );

        subject.onFeaturesRefresh(failingCallback);
        subject.onFeaturesRefresh(successfulCallback);
        subject.initialize();

        verify(successfulCallback).onRefresh("{\"test-feature\":{\"defaultValue\":true}}");
        subject.shutdown();
    }

    @Test
    void testFeatureRefreshListener_receivesInitializationMetadata() throws IOException, FeatureFetchException {
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                60,
                mockHttpClient("{\"features\":{\"test-feature\":{\"defaultValue\":true}}}"),
                true,
                null,
                null
        );
        subject.addFeatureRefreshListener(listener);

        subject.initialize();

        ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
        verify(listener).onRefresh(eventCaptor.capture());
        FeatureRefreshEvent event = eventCaptor.getValue();
        assertTrue(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(1, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertTrue(event.getDurationMillis() >= 0);
        subject.shutdown();
    }

    @Test
    void testFeatureRefreshListener_receivesManualNotModifiedMetadata() throws IOException, FeatureFetchException {
        OkHttpClient mockOkHttpClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);
        Response response = mock(Response.class);
        when(mockOkHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(response);
        when(response.code()).thenReturn(304);

        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                60,
                mockOkHttpClient,
                true,
                null,
                null
        );
        subject.addFeatureRefreshListener(listener);

        subject.fetchFeatures();

        ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
        verify(listener).onRefresh(eventCaptor.capture());
        FeatureRefreshEvent event = eventCaptor.getValue();
        assertTrue(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
    }

    @Test
    void testFeatureRefreshListener_continuesAfterListenerFailure() throws IOException, FeatureFetchException {
        FeatureRefreshListener failingListener = mock(FeatureRefreshListener.class);
        FeatureRefreshListener successfulListener = mock(FeatureRefreshListener.class);
        doThrow(new RuntimeException("listener failed")).when(failingListener).onRefresh(any());
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                60,
                mockHttpClient("{\"features\":{}}"),
                true,
                null,
                null
        );
        subject.addFeatureRefreshListener(failingListener);
        subject.addFeatureRefreshListener(successfulListener);

        subject.fetchFeatures();

        verify(successfulListener).onRefresh(any(FeatureRefreshEvent.class));
    }

    @Test
    void shutdownClearsRegisteredListeners() throws IOException, FeatureFetchException {
        OkHttpClient mockOkHttpClient = mock(OkHttpClient.class);
        Call mockCall = mock(Call.class);
        Response response = mock(Response.class);
        when(mockOkHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(response);
        when(response.code()).thenReturn(304);

        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                60,
                mockOkHttpClient,
                true,
                null,
                null
        );
        subject.addFeatureRefreshListener(listener);

        subject.fetchFeatures();
        verify(listener, times(1)).onRefresh(any(FeatureRefreshEvent.class));

        // shutdown must release listeners so a shut-down repository notifies no one
        subject.shutdown();
        subject.fetchFeatures();

        verify(listener, times(1)).onRefresh(any(FeatureRefreshEvent.class));
    }

    @Test
    void testOnFeaturesRefresh_Error() throws IOException {
        OkHttpClient mockOkHttpClient = mock(OkHttpClient.class);
        IOException requestFailed = new IOException("Request failed");

        Call mockCall = mock(Call.class);
        doReturn(mockCall).when(mockOkHttpClient).newCall(any(Request.class));
        when(mockCall.execute()).thenThrow(requestFailed);

        FeatureRefreshCallback featureRefreshCallback = mock(FeatureRefreshCallback.class);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                null,
                0,
                mockOkHttpClient,
                null,
                null,
                null,
                null,
                null,
                NO_DELAY_RETRY_POLICY
        );

        subject.onFeaturesRefresh(featureRefreshCallback);

        try { subject.initialize(); } catch (Exception ignored) {}

        verify(featureRefreshCallback).onError(requestFailed);
        verify(featureRefreshCallback, never()).onRefresh(anyString());

        subject.getCacheManager().clearCache();
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
                true,
                null,
                null
        ));

        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();
        String expectedResponse = "{\"features\": {}}";
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        repository.addFeatureRefreshListener(listener);



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
        ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
        verify(listener).onRefresh(eventCaptor.capture());
        assertEquals(FeatureRefreshSource.REMOTE_EVALUATION, eventCaptor.getValue().getSource());
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
                true,
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
                true,
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
                requestBody,
                null
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
                true,
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
        FileCachingManagerImpl mockCacheManager = mock(FileCachingManagerImpl.class);
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
                null,
                null,
                null,
                NO_DELAY_RETRY_POLICY
        );

        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Http error"));
        when(mockCacheManager.loadCache(anyString())).thenReturn(cachedData);

        subject.setCacheManager(mockCacheManager);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        subject.addFeatureRefreshListener(listener);
        subject.initialize();
        String actualResult = subject.getFeaturesJson();
        assertEquals(expectedResult, actualResult);
        verify(mockCacheManager).loadCache(anyString());
        ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
        verify(listener).onRefresh(eventCaptor.capture());
        FeatureRefreshEvent event = eventCaptor.getValue();
        assertFalse(event.isSuccessful());
        assertTrue(event.isLoadedFromCache());
        assertTrue(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        mockCacheManager.clearCache();
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
