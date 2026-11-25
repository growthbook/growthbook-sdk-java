package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.internal.FeatureEndpoints;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract the repositories fetching features over HTTP share, on top of
 * {@link FeaturesRepositoryContractTest}: requests, decryption, HTTP and network failures, the
 * cache fallback, ETags, saved groups and shutdown. The server is WireMock; retries have no delay.
 *
 * @param <R> the repository under test
 */
abstract class HttpFeaturesRepositoryContractTest<R extends IGBFeaturesRepository> extends FeaturesRepositoryContractTest<R> {

    static final int SWR_TTL_SECONDS = 60;

    private static final String CLIENT_KEY = "sdk-contract123";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final int MAX_ATTEMPTS = 5;
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(MAX_ATTEMPTS, Duration.ZERO, Duration.ZERO);
    private static final String ETAG = "W/\"contract-v1\"";
    private static final String SAVED_GROUPS = "{\"beta_testers\":[\"user-1\",\"user-2\"]}";
    private static final String CACHED_FEATURES = "{\"cached_flag\":{\"defaultValue\":true}}";
    private static final String DECRYPTION_KEY = "o0maZL/O7AphxcbRvaJIzw==";
    private static final String ENCRYPTED_RESPONSE = "{\"status\":200,\"features\":{},\"dateUpdated\":\"2023-01-25T00:51:26.772Z\","
            + "\"encryptedFeatures\":\"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"}";
    private static final String DECRYPTED_FEATURES = "{\"targeted_percentage_rollout\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":\"foo\"},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"test_feature\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":{\"$not\":{\"$regex\":\"foo\"},\"$eq\":\"\"}},\"force\":true}]},\"sample_json\":{\"defaultValue\":{}},\"string_feature\":{\"defaultValue\":\"hello, world!\"},\"some_test_feature\":{\"defaultValue\":true},\"my_new_feature_jan17_5\":{\"defaultValue\":true},\"my_new_feature_jan17_13\":{\"defaultValue\":true}}";

    private WireMockServer wireMock;

    /**
     * Creates the repository under test with the stale-while-revalidate strategy and a TTL of
     * {@link #SWR_TTL_SECONDS}.
     *
     * @param cacheManager the cache to use, or {@code null} to disable caching
     */
    abstract R createRepository(String apiHost, String clientKey, @Nullable String decryptionKey,
                                @Nullable GbCacheManager cacheManager, FeatureFetchRetryPolicy retryPolicy);

    abstract void refresh(R repository) throws FeatureFetchException;

    abstract boolean isInitialized(R repository);

    abstract String savedGroupsJson(R repository);

    abstract void shutdown(R repository);

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Override
    final R createRepository() {
        return createRepository(apiHost(), CLIENT_KEY, null, null, NO_DELAY_RETRY_POLICY);
    }

    @Override
    final void givenSourceHolds(String featuresJson) {
        stubFeaturesResponse("{\"status\":200,\"features\":" + featuresJson + "}");
    }

    @Override
    final void givenSourceIsUnavailable() {
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    @Override
    final FeatureFetchErrorCode unavailableSourceErrorCode() {
        return FeatureFetchErrorCode.NO_RESPONSE_ERROR;
    }

    @Override
    final void release(R repository) {
        shutdown(repository);
    }

    @Test
    @DisplayName("Initialize sends one unconditional request bounded by the TTL and marks the repository initialized")
    void initializeSendsOneUnconditionalRequest() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();

        // When
        subject.initialize();

        // Then
        assertTrue(isInitialized(subject));
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH))
                .withoutHeader("If-None-Match")
                .withHeader("Cache-Control", equalTo("max-age=" + SWR_TTL_SECONDS)));
    }

    @Test
    @DisplayName("Initializing twice fetches the features once")
    void initializingTwiceFetchesOnce() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();
        subject.initialize();

        // When
        subject.initialize();

        // Then
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH)));
        assertTrue(isInitialized(subject));
    }

    @Test
    @DisplayName("Reading before initialize sends no request and exposes empty saved groups")
    void readingBeforeInitializeSendsNoRequest() {
        // Given
        R subject = newRepository();

        // When
        String featuresJson = subject.getFeaturesJson();
        String savedGroupsJson = savedGroupsJson(subject);

        // Then
        assertEquals("{}", featuresJson);
        assertEquals("{}", savedGroupsJson);
        assertFalse(isInitialized(subject));
        wireMock.verify(0, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("A manual refresh before initialize loads the features without initializing the repository")
    void manualRefreshBeforeInitializeLoadsFeatures() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();

        // When
        refresh(subject);

        // Then
        assertEquals(FEATURES, subject.getFeaturesJson());
        assertFalse(isInitialized(subject));
    }

    @Test
    @DisplayName("Initialize decrypts an encrypted payload with the decryption key")
    void initializeDecryptsEncryptedPayload() throws Exception {
        // Given
        stubFeaturesResponse(ENCRYPTED_RESPONSE);
        R subject = track(createRepository(apiHost(), CLIENT_KEY, DECRYPTION_KEY, null, NO_DELAY_RETRY_POLICY));

        // When
        subject.initialize();

        // Then
        assertEquals(DECRYPTED_FEATURES, subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Initialize fails with a configuration error when a decryption key is set but the payload is not encrypted")
    void initializeFailsWhenPayloadIsNotEncrypted() {
        // Given
        givenSourceHolds(FEATURES);
        R subject = track(createRepository(apiHost(), CLIENT_KEY, DECRYPTION_KEY, null, NO_DELAY_RETRY_POLICY));

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals("{}", subject.getFeaturesJson());
        assertFalse(isInitialized(subject));
    }

    @ParameterizedTest(name = "status={0}")
    @CsvSource({"400, 1", "404, 1", "500, 5", "503, 5"})
    @DisplayName("Initialize fails with an HTTP response error, retrying only a retryable status")
    void initializeFailsOnErrorStatus(int status, int expectedRequests) {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH))
                .willReturn(aResponse().withStatus(status).withBody("{\"status\":" + status + ",\"error\":\"synthetic failure\"}")));
        R subject = newRepository();

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.HTTP_RESPONSE_ERROR, exception.getErrorCode());
        assertEquals("HTTP_RESPONSE_ERROR : responded with status " + status, exception.getMessage());
        assertEquals("{}", subject.getFeaturesJson());
        assertFalse(isInitialized(subject));
        wireMock.verify(expectedRequests, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("A network fault fails initialize with a no-response error, reports the I/O error and leaves the repository uninitialized")
    void networkFaultFailsInitialize() {
        // Given
        givenSourceIsUnavailable();
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
        assertFalse(isInitialized(subject));
        assertInstanceOf(IOException.class, listener.events.get(0).getError());
    }

    @Test
    @DisplayName("Initialize falls back to the cached payload on a network failure and reports it as loaded from cache")
    void initializeFallsBackToCacheOnNetworkFailure() throws Exception {
        // Given
        givenSourceIsUnavailable();
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        cacheManager.store.put(cacheKey(), "{\"status\":200,\"features\":" + CACHED_FEATURES + "}");
        R subject = track(createRepository(apiHost(), CLIENT_KEY, null, cacheManager, NO_DELAY_RETRY_POLICY));
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        subject.initialize();

        // Then
        assertEquals(CACHED_FEATURES, subject.getFeaturesJson());
        assertTrue(isInitialized(subject));
        assertEquals(Boolean.TRUE, subject.getLastRefreshLoadedFromCache());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertTrue(event.isLoadedFromCache());
        assertTrue(event.isFeaturesChanged());
        assertEquals(1, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
    }

    @Test
    @DisplayName("A network failure does not fall back to another client's entry under the legacy cache key")
    void networkFailureIgnoresLegacyCacheEntry() {
        // Given
        givenSourceIsUnavailable();
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        cacheManager.store.put("FEATURE_CACHE.json", "{\"features\":" + CACHED_FEATURES + "}");
        R subject = track(createRepository(apiHost(), CLIENT_KEY, null, cacheManager, NO_DELAY_RETRY_POLICY));

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
        assertEquals("{}", subject.getFeaturesJson());
    }

    @Test
    @DisplayName("A successful fetch saves the response under the endpoint's own cache key only")
    void successfulFetchSavesResponseUnderEndpointCacheKey() throws Exception {
        // Given
        String response = "{\"status\":200,\"features\":" + FEATURES + "}";
        stubFeaturesResponse(response);
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        R subject = track(createRepository(apiHost(), CLIENT_KEY, null, cacheManager, NO_DELAY_RETRY_POLICY));

        // When
        refresh(subject);

        // Then
        assertEquals(1, cacheManager.store.size());
        assertEquals(response, cacheManager.store.get(cacheKey()));
    }

    @Test
    @DisplayName("A later refresh sends the remembered ETag and keeps the features when the server answers 304")
    void laterRefreshKeepsFeaturesOnNotModified() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH))
                .withHeader("If-None-Match", absent())
                .willReturn(aResponse().withStatus(200).withHeader("ETag", ETAG)
                        .withBody("{\"status\":200,\"features\":" + FEATURES + "}")));
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH))
                .withHeader("If-None-Match", equalTo(ETAG))
                .willReturn(aResponse().withStatus(304)));
        R subject = newRepository();
        subject.initialize();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        refresh(subject);

        // Then
        wireMock.verify(2, getRequestedFor(urlEqualTo(FEATURES_PATH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH)).withHeader("If-None-Match", equalTo(ETAG)));
        assertEquals(FEATURES, subject.getFeaturesJson());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertTrue(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
    }

    @Test
    @DisplayName("A manual refresh answered 304 without held features is an unchanged success")
    void notModifiedWithoutHeldFeaturesIsUnchangedSuccess() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withStatus(304)));
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        refresh(subject);

        // Then
        assertEquals("{}", subject.getFeaturesJson());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertTrue(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(0, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
    }

    @Test
    @DisplayName("Exposes the saved groups of the payload")
    void exposesSavedGroups() throws Exception {
        // Given
        stubFeaturesResponse("{\"status\":200,\"features\":" + FEATURES + ",\"savedGroups\":" + SAVED_GROUPS + "}");
        R subject = newRepository();

        // When
        subject.initialize();

        // Then
        assertEquals(SAVED_GROUPS, savedGroupsJson(subject));
        assertEquals(FEATURES, subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Exposes empty saved groups when the payload has none")
    void exposesEmptySavedGroupsWhenPayloadHasNone() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();

        // When
        subject.initialize();

        // Then
        assertEquals("", savedGroupsJson(subject));
    }

    @Test
    @DisplayName("Shutdown stops listener notifications and keeps the held features")
    void shutdownStopsNotificationsAndKeepsFeatures() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);
        subject.initialize();

        // When
        shutdown(subject);
        refresh(subject);

        // Then
        assertEquals(1, listener.events.size());
        assertEquals(FEATURES, subject.getFeaturesJson());
    }

    private void stubFeaturesResponse(String body) {
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private String apiHost() {
        return "http://localhost:" + wireMock.port();
    }

    private String cacheKey() {
        return FeatureEndpoints.of(apiHost(), CLIENT_KEY).cacheKey();
    }
}
