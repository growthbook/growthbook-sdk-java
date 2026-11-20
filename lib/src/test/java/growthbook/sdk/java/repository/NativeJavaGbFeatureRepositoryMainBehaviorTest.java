package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.testhelpers.FixedPayloadCacheManager;
import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeJavaGbFeatureRepositoryMainBehaviorTest {

    private static final String CLIENT_KEY = "sdk-test123";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final String REMOTE_EVAL_PATH = "/api/eval/" + CLIENT_KEY;
    private static final String FEATURES_RESPONSE = "{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}";
    private static final String FEATURES = "{\"dark-mode\":{\"defaultValue\":true}}";
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
    @DisplayName("Initialize with the SSE strategy loads the features when the server enables SSE")
    void initializeWithSseStrategyLoadsFeatures() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("X-Sse-Support", "enabled")
                .withHeader("Content-Type", "application/json")
                .withBody(FEATURES_RESPONSE)));
        wireMock.stubFor(get(urlEqualTo("/sub/" + CLIENT_KEY)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody("")));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build());

        // When
        subject.initialize();

        // Then
        assertTrue(subject.getInitialized().get());
        assertEquals(FeatureRefreshStrategy.SERVER_SENT_EVENTS, subject.getRefreshStrategy());
        assertEquals(FEATURES, subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Initialize with the remote evaluation strategy posts and loads the evaluated features")
    void initializeWithRemoteEvalStrategyLoadsFeatures() throws Exception {
        // Given
        wireMock.stubFor(post(urlEqualTo(REMOTE_EVAL_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(FEATURES_RESPONSE)));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .build());

        // When
        subject.initialize();

        // Then
        assertTrue(subject.getInitialized().get());
        assertEquals(FEATURES, subject.getFeaturesJson());
    }

    @Test
    @DisplayName("Remote evaluation calls the legacy error callback on a network failure")
    @SuppressWarnings("deprecation")
    void remoteEvaluationCallsLegacyErrorCallbackOnNetworkFailure() {
        // Given
        wireMock.stubFor(post(urlEqualTo(REMOTE_EVAL_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().build());
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        subject.onFeaturesRefresh(callback);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> subject.fetchForRemoteEval(null));

        // Then
        assertEquals(FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
        assertEquals(1, callback.errors.size());
        assertTrue(callback.refreshes.isEmpty());
    }

    @Test
    @DisplayName("Fetching falls back to the cached features on a network failure")
    void fetchingFallsBackToCacheOnNetworkFailure() throws Exception {
        // Given
        stubNetworkFailure();
        FixedPayloadCacheManager cacheManager = new FixedPayloadCacheManager(FEATURES_RESPONSE);
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .isCacheDisabled(false)
                .cacheManager(cacheManager)
                .build());

        // When
        subject.fetchFeatures();

        // Then
        assertEquals(FEATURES, subject.getFeaturesJson());
        assertEquals(1, cacheManager.loadCount());
    }

    @Test
    @DisplayName("Reading the features after the TTL expired refreshes them on the reading thread")
    void readingAfterTtlExpiredRefreshes() throws Exception {
        // Given
        stubFeatures(FEATURES_RESPONSE);
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().swrTtlSeconds(0).build());
        subject.initialize();

        // When
        String featuresJson = subject.getFeaturesJson();

        // Then
        assertEquals(FEATURES, featuresJson);
        wireMock.verify(2, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("Reading the features within the TTL does not refresh them")
    void readingWithinTtlDoesNotRefresh() throws Exception {
        // Given
        stubFeatures(FEATURES_RESPONSE);
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder().swrTtlSeconds(60).build());
        subject.initialize();

        // When
        subject.getFeaturesJson();
        subject.getFeaturesMap();

        // Then
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("A TTL updated after construction applies to the next request")
    void ttlUpdatedAfterConstructionAppliesToNextRequest() throws Exception {
        // Given
        stubFeatures(FEATURES_RESPONSE);
        NativeJavaGbFeatureRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(60)
                .build());
        subject.initialize();

        // When
        subject.getSwrTtlSeconds().set(5);
        subject.refreshFeatures();

        // Then
        wireMock.verify(getRequestedFor(urlEqualTo(FEATURES_PATH)).withHeader("Cache-Control", equalTo("max-age=5")));
    }

    private NativeJavaGbFeatureRepository.NativeJavaGbFeatureRepositoryBuilder repositoryBuilder() {
        return NativeJavaGbFeatureRepository.builder()
                .apiHost(apiHost())
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private String apiHost() {
        return "http://localhost:" + wireMock.port();
    }

    private void stubFeatures(String body) {
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("X-Sse-Support", "disabled")
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private void stubNetworkFailure() {
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    private NativeJavaGbFeatureRepository track(NativeJavaGbFeatureRepository repository) {
        repositories.add(repository);
        return repository;
    }
}
