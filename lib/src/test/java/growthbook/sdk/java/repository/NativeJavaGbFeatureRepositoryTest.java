package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.testhelpers.FixedPayloadCacheManager;
import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeJavaGbFeatureRepositoryTest {

    private static final String CLIENT_KEY = "sdk-test";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final String ENCRYPTION_KEY = "BhB1wORFmZLTDjbvstvS8w==";
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

    private final List<NativeJavaGbFeatureRepository> repositories = new ArrayList<>();
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        for (NativeJavaGbFeatureRepository repository : repositories) {
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
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                apiHost, CLIENT_KEY, null, null, null, null, null, null);

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-test", subject.getFeaturesEndpoint());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
        assertNull(subject.getEncryptionKey());
    }

    @Test
    @DisplayName("The constructor keeps the encryption key")
    void constructorKeepsEncryptionKey() {
        // Given
        String apiHost = "https://cdn.example.test";

        // When
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                apiHost, CLIENT_KEY, ENCRYPTION_KEY, null, null, null, null, null);

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-test", subject.getFeaturesEndpoint());
        assertEquals(ENCRYPTION_KEY, subject.getEncryptionKey());
    }

    @Test
    @DisplayName("The builder builds the features endpoint without an encryption key")
    void builderBuildsEndpointWithoutEncryptionKey() {
        // Given
        NativeJavaGbFeatureRepository.NativeJavaGbFeatureRepositoryBuilder builder = NativeJavaGbFeatureRepository.builder()
                .apiHost("https://cdn.example.test")
                .clientKey(CLIENT_KEY);

        // When
        NativeJavaGbFeatureRepository subject = builder.build();

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-test", subject.getFeaturesEndpoint());
        assertNull(subject.getEncryptionKey());
    }

    @Test
    @DisplayName("The builder keeps the encryption key")
    void builderKeepsEncryptionKey() {
        // Given
        NativeJavaGbFeatureRepository.NativeJavaGbFeatureRepositoryBuilder builder = NativeJavaGbFeatureRepository.builder()
                .apiHost("https://cdn.example.test")
                .clientKey(CLIENT_KEY)
                .encryptionKey(ENCRYPTION_KEY);

        // When
        NativeJavaGbFeatureRepository subject = builder.build();

        // Then
        assertEquals("https://cdn.example.test/api/features/sdk-test", subject.getFeaturesEndpoint());
        assertEquals(ENCRYPTION_KEY, subject.getEncryptionKey());
    }

    @Test
    @DisplayName("Rejects a null client key")
    void rejectsNullClientKey() {
        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new NativeJavaGbFeatureRepository("https://cdn.example.test", null, null, null, null, null, null, null));

        // Then
        assertEquals("clientKey cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Reports a successful refresh to legacy callbacks")
    @SuppressWarnings("deprecation")
    void reportsSuccessfulRefreshToLegacyCallbacks() {
        // Given
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);

        // When
        subject.onRefreshSuccess("{}");

        // Then
        assertEquals(1, callback.refreshes.size());
        assertEquals("{}", callback.refreshes.get(0));
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    @DisplayName("A throwing legacy callback does not stop the next callback on success")
    @SuppressWarnings("deprecation")
    void throwingCallbackDoesNotStopNextCallbackOnSuccess() {
        // Given
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(new ThrowingRefreshCallback());
        subject.onFeaturesRefresh(callback);

        // When
        subject.onRefreshSuccess("{}");

        // Then
        assertEquals(1, callback.refreshes.size());
        assertEquals("{}", callback.refreshes.get(0));
    }

    @Test
    @DisplayName("Reports a failed refresh to legacy callbacks")
    @SuppressWarnings("deprecation")
    void reportsFailedRefreshToLegacyCallbacks() {
        // Given
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);
        Exception error = new Exception("Error");

        // When
        subject.onRefreshFailed(error);

        // Then
        assertEquals(1, callback.errors.size());
        assertSame(error, callback.errors.get(0));
        assertTrue(callback.refreshes.isEmpty());
    }

    @Test
    @DisplayName("A throwing legacy callback does not stop the next callback on failure")
    @SuppressWarnings("deprecation")
    void throwingCallbackDoesNotStopNextCallbackOnFailure() {
        // Given
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(new ThrowingRefreshCallback());
        subject.onFeaturesRefresh(callback);
        RuntimeException error = new RuntimeException("refresh failed");

        // When
        subject.onRefreshFailed(error);

        // Then
        assertEquals(1, callback.errors.size());
        assertSame(error, callback.errors.get(0));
    }

    @Test
    @DisplayName("Cleared legacy callbacks are no longer notified")
    @SuppressWarnings("deprecation")
    void clearedCallbacksAreNotNotified() {
        // Given
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);
        subject.clearCallbacks();

        // When
        subject.onRefreshSuccess("{}");
        subject.onRefreshFailed(new RuntimeException("refresh failed"));

        // Then
        assertTrue(callback.refreshes.isEmpty());
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    @DisplayName("A listener receives a cache fallback after the server keeps failing")
    void listenerReceivesCacheFallback() throws Exception {
        // Given
        stubFeatures(aResponse().withStatus(500).withBody("{\"error\":\"temporary failure\"}"));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .isCacheDisabled(false)
                .cacheManager(new FixedPayloadCacheManager("{\"features\":{\"cached-feature\":{\"defaultValue\":true}}}"))
                .build());
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        subject.fetchFeatures();

        // Then
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertTrue(event.isLoadedFromCache());
        assertEquals(1, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
    }

    @ParameterizedTest(name = "X-Sse-Support={0}")
    @ValueSource(strings = {"", "disabled"})
    @DisplayName("Initialize applies the payload when the server does not enable SSE")
    void initializeAppliesPayloadWithoutSseSupport(String sseSupport) throws Exception {
        // Given
        stubFeatures(withSseSupport(ok("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}"), sseSupport));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .build());

        // When
        subject.initialize();

        // Then
        assertTrue(subject.getInitialized().get());
        assertEquals("{\"dark-mode\":{\"defaultValue\":true}}", subject.getFeaturesJson());
    }

    @ParameterizedTest(name = "X-Sse-Support={0}")
    @ValueSource(strings = {"", "disabled"})
    @DisplayName("An SSE repository falls back to stale-while-revalidate when the server does not enable SSE")
    void sseRepositoryFallsBackWithoutSseSupport(String sseSupport) throws Exception {
        // Given
        stubFeatures(withSseSupport(ok("{\"features\":{}}"), sseSupport));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build());

        // When
        subject.initialize();

        // Then
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
    }

    @Test
    @DisplayName("Does not remember the ETag of a response whose payload failed to apply")
    void doesNotRememberETagOfFailedPayload() throws Exception {
        // Given
        stubFeatures(ok("null").withHeader("ETag", "W/\"stale-etag\""));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        FeatureFetchException failure = assertThrows(FeatureFetchException.class, subject::fetchFeatures);
        stubFeatures(ok("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}").withHeader("ETag", "W/\"fresh-etag\""));

        // When
        subject.fetchFeatures();

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, failure.getErrorCode());
        assertEquals("{\"dark-mode\":{\"defaultValue\":true}}", subject.getFeaturesJson());
        wireMock.verify(0, getRequestedFor(urlEqualTo(FEATURES_PATH)).withHeader("If-None-Match", matching(".*")));
    }

    private NativeJavaGbFeatureRepository.NativeJavaGbFeatureRepositoryBuilder repositoryBuilder() {
        return NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private NativeJavaGbFeatureRepository track(NativeJavaGbFeatureRepository repository) {
        repositories.add(repository);
        return repository;
    }

    private void stubFeatures(ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(response));
    }

    private static ResponseDefinitionBuilder ok(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static ResponseDefinitionBuilder withSseSupport(ResponseDefinitionBuilder response, String sseSupport) {
        return sseSupport.isEmpty() ? response : response.withHeader("X-Sse-Support", sseSupport);
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
