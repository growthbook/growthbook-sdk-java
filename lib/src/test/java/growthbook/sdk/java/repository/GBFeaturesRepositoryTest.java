package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.repository.internal.FeatureEndpoints;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GBFeaturesRepositoryTest {

    private static final String CLIENT_KEY = "sdk-abc123";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final String REMOTE_EVAL_PATH = "/api/eval/" + CLIENT_KEY;
    private static final String ENCRYPTION_KEY = "BhB1wORFmZLTDjbvstvS8w==";
    private static final String FEATURES = "{\"banner_text\":{\"defaultValue\":\"Welcome\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue\"}]},"
            + "\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]}}";
    private static final String FEATURES_RESPONSE = "{\"status\":200,\"features\":" + FEATURES + ",\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

    private final List<GBFeaturesRepository> repositories = new ArrayList<>();
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        for (GBFeaturesRepository repository : repositories) {
            repository.shutdown();
        }
        wireMock.stop();
    }

    @Test
    @DisplayName("The constructor builds the features endpoint and defaults to stale-while-revalidate")
    void constructorBuildsEndpointAndDefaultsToStaleWhileRevalidate() {
        // Given
        String apiHost = "https://cdn.example.test";

        // When
        GBFeaturesRepository subject = new GBFeaturesRepository(
                apiHost, CLIENT_KEY, null, null, null, null, true, null, null);

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-abc123", subject.getFeaturesEndpoint());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
        assertNull(subject.getDecryptionKey());
    }

    @Test
    @DisplayName("The constructor keeps the decryption key")
    void constructorKeepsDecryptionKey() {
        // Given
        String apiHost = "https://cdn.example.test";

        // When
        GBFeaturesRepository subject = new GBFeaturesRepository(
                apiHost, CLIENT_KEY, ENCRYPTION_KEY, null, null, null, true, null, null);

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-abc123", subject.getFeaturesEndpoint());
        assertEquals(ENCRYPTION_KEY, subject.getDecryptionKey());
    }

    @Test
    @DisplayName("The builder builds the features endpoint without a decryption key")
    void builderBuildsEndpointWithoutDecryptionKey() {
        // Given
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = GBFeaturesRepository.builder()
                .apiHost("https://cdn.example.test")
                .clientKey(CLIENT_KEY);

        // When
        GBFeaturesRepository subject = builder.build();

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-abc123", subject.getFeaturesEndpoint());
        assertNull(subject.getDecryptionKey());
    }

    @Test
    @DisplayName("The builder uses the encryption key as the decryption key")
    void builderUsesEncryptionKeyAsDecryptionKey() {
        // Given
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = GBFeaturesRepository.builder()
                .apiHost("https://cdn.example.test")
                .clientKey(CLIENT_KEY)
                .encryptionKey(ENCRYPTION_KEY);

        // When
        GBFeaturesRepository subject = builder.build();

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-abc123", subject.getFeaturesEndpoint());
        assertEquals(ENCRYPTION_KEY, subject.getDecryptionKey());
    }

    @Test
    @DisplayName("A successful refresh calls the legacy callback with the features and no error")
    @SuppressWarnings("deprecation")
    void successfulRefreshCallsLegacyCallback() throws Exception {
        // Given
        stubFeatures(FEATURES_RESPONSE);
        GBFeaturesRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);

        // When
        subject.initialize();

        // Then
        assertEquals(1, callback.refreshes.size());
        assertEquals(FEATURES, callback.refreshes.get(0));
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    @DisplayName("A throwing legacy callback does not stop the next callback")
    @SuppressWarnings("deprecation")
    void throwingLegacyCallbackDoesNotStopNextCallback() throws Exception {
        // Given
        stubFeatures("{\"features\":{\"test-feature\":{\"defaultValue\":true}}}");
        GBFeaturesRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(new ThrowingRefreshCallback());
        subject.onFeaturesRefresh(callback);

        // When
        subject.initialize();

        // Then
        assertEquals(1, callback.refreshes.size());
        assertEquals("{\"test-feature\":{\"defaultValue\":true}}", callback.refreshes.get(0));
    }

    @Test
    @DisplayName("A network failure without cached features calls the legacy error callback and fails initialize")
    @SuppressWarnings("deprecation")
    void networkFailureCallsLegacyErrorCallback() {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        GBFeaturesRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
        assertEquals(1, callback.errors.size());
        assertInstanceOf(IOException.class, callback.errors.get(0));
        assertTrue(callback.refreshes.isEmpty());
    }

    @Test
    @DisplayName("Shutting down an initialized SSE repository disconnects it and keeps its features")
    void shutdownDisconnectsSseAndKeepsFeatures() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("X-Sse-Support", "enabled")
                .withBody("{\"features\":{\"flag\":{\"defaultValue\":true}}}")));
        wireMock.stubFor(get(urlEqualTo("/sub/" + CLIENT_KEY)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("")));
        GBFeaturesRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build());
        subject.initialize();

        // When
        subject.shutdown();

        // Then
        assertFalse(subject.isSseConnected());
        assertEquals("{\"flag\":{\"defaultValue\":true}}", subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Remote evaluation stores the features and notifies listeners with the remote evaluation source")
    void remoteEvaluationStoresFeatures() throws Exception {
        // Given
        wireMock.stubFor(post(urlEqualTo(REMOTE_EVAL_PATH)).willReturn(aResponse().withStatus(200).withBody("{\"features\": {}}")));
        GBFeaturesRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .build());
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        subject.fetchForRemoteEval(new RequestBodyForRemoteEval());

        // Then
        assertEquals("{}", subject.getFeaturesJson());
        wireMock.verify(1, postRequestedFor(urlEqualTo(REMOTE_EVAL_PATH)));
        assertEquals(1, listener.events.size());
        assertTrue(listener.events.get(0).isSuccessful());
        assertEquals(FeatureRefreshSource.REMOTE_EVALUATION, listener.events.get(0).getSource());
    }

    @Test
    @DisplayName("Remote evaluation fails with a no-response error on a network failure")
    void remoteEvaluationFailsOnNetworkFailure() {
        // Given
        wireMock.stubFor(post(urlEqualTo(REMOTE_EVAL_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        GBFeaturesRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .build());
        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class,
                () -> subject.fetchForRemoteEval(requestBody));

        // Then
        assertEquals(FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("Remote evaluation reports an error status to the legacy error callback instead of throwing")
    @SuppressWarnings("deprecation")
    void remoteEvaluationReportsErrorStatusToCallback() throws Exception {
        // Given
        wireMock.stubFor(post(urlEqualTo(REMOTE_EVAL_PATH))
                .willReturn(aResponse().withStatus(500).withStatusMessage("Internal Server Error")));
        GBFeaturesRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);

        // When
        subject.fetchForRemoteEval(new RequestBodyForRemoteEval());

        // Then
        assertEquals(1, callback.errors.size());
        assertEquals("Response is not success, response code is:500. And message is: Internal Server Error",
                callback.errors.get(0).getMessage());
        assertEquals("{}", subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Remote evaluation posts the request body as UTF-8 JSON")
    void remoteEvaluationPostsJsonBody() throws Exception {
        // Given
        wireMock.stubFor(post(urlEqualTo(REMOTE_EVAL_PATH)).willReturn(aResponse().withStatus(200).withBody("{\"features\": {}}")));
        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();
        requestBody.setAttributes(new JsonObject());
        requestBody.setUrl("");
        requestBody.setForcedFeatures(new ArrayList<>());
        requestBody.setForcedVariations(new HashMap<>());
        GBFeaturesRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .requestBodyForRemoteEval(requestBody)
                .build());

        // When
        subject.fetchForRemoteEval(requestBody);

        // Then
        wireMock.verify(1, postRequestedFor(urlEqualTo(REMOTE_EVAL_PATH))
                .withHeader("Content-Type", equalToIgnoreCase("application/json; charset=utf-8"))
                .withRequestBody(equalToJson("{\"attributes\":{},\"forcedFeatures\":[],\"forcedVariations\":{},\"url\":\"\"}")));
    }

    @Test
    @DisplayName("Initialize falls back to the cached features when the network fails and reports it to listeners")
    void initializeFallsBackToCacheOnNetworkFailure() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        cacheManager.store.put(cacheKey(), FEATURES_RESPONSE);
        GBFeaturesRepository subject = track(repositoryBuilder().isCacheDisabled(false).build());
        subject.setCacheManager(cacheManager);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        subject.initialize();

        // Then
        assertEquals(FEATURES, subject.getFeaturesJson());
        assertSame(cacheManager, subject.getCacheManager());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertTrue(event.isLoadedFromCache());
        assertTrue(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
    }

    @Test
    @DisplayName("A cache fallback that matches the held features is reported as loaded but unchanged")
    void cacheFallbackMatchingHeldFeaturesIsUnchanged() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        cacheManager.store.put(cacheKey(), "{\"status\":200,\"features\":{},\"savedGroups\":{}}");
        GBFeaturesRepository subject = track(repositoryBuilder().isCacheDisabled(false).cacheManager(cacheManager).build());
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        subject.initialize();

        // Then
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertTrue(event.isLoadedFromCache());
        assertFalse(event.isFeaturesChanged());
    }

    private GBFeaturesRepository.GBFeaturesRepositoryBuilder repositoryBuilder() {
        return GBFeaturesRepository.builder()
                .apiHost(apiHost())
                .clientKey(CLIENT_KEY)
                .swrTtlSeconds(60)
                .okHttpClient(new OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private String apiHost() {
        return "http://localhost:" + wireMock.port();
    }

    private String cacheKey() {
        return FeatureEndpoints.of(apiHost(), CLIENT_KEY).cacheKey();
    }

    private void stubFeatures(String body) {
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withStatus(200).withBody(body)));
    }

    private GBFeaturesRepository track(GBFeaturesRepository repository) {
        repositories.add(repository);
        return repository;
    }

    @SuppressWarnings("deprecation")
    private static final class ThrowingRefreshCallback implements growthbook.sdk.java.callback.FeatureRefreshCallback {
        @Override
        public void onRefresh(String featuresJson) {
            throw new IllegalStateException("callback failed");
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException("callback failed");
        }
    }
}
