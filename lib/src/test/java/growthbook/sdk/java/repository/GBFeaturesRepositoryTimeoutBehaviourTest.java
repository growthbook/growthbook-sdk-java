package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@code requestTimeout} against a real HTTP server whose features body arrives slowly, in
 * chunks spaced closer than the read timeout, so only the whole-call timeout can cut it.
 */
class GBFeaturesRepositoryTimeoutBehaviourTest {

    private static final String CLIENT_KEY = "sdk-timeout-behaviour";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final String FEATURES = "{\"features\":{\"flag\":{\"defaultValue\":true}}}";
    private static final String FEATURES_JSON = "{\"flag\":{\"defaultValue\":true}}";
    private static final FeatureFetchRetryPolicy SINGLE_ATTEMPT =
            new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    /** Four chunks over 1.2 s: 400 ms apart, well under the 1 s read timeout below. */
    private static final int CHUNKS = 4;
    private static final int SLOW_BODY_MILLIS = 1_200;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(1_000);

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @Timeout(10)
    @DisplayName("An explicit requestTimeout cuts a features body that keeps trickling in")
    void requestTimeoutCutsSlowBody() {
        // Given
        stubSlowBody();
        GBFeaturesRepository subject = repository(REQUEST_TIMEOUT);

        // When & Then
        assertThrows(FeatureFetchException.class, subject::fetchFeatures);
        subject.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("Without requestTimeout a slow but steady features body is read to the end, as before")
    void unsetRequestTimeoutWaitsForSlowBody() throws FeatureFetchException {
        // Given
        stubSlowBody();
        GBFeaturesRepository subject = repository(null);

        // When
        subject.fetchFeatures();

        // Then
        assertEquals(FEATURES_JSON, subject.getFeaturesJson());
        subject.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("A body cut by the timeout does not cache its ETag, so the retry is not a 304 for unstored features")
    void timedOutBodyDoesNotCacheETag() throws FeatureFetchException {
        // Given: the first response carries an ETag and a slow body; later ones are fast, and a request
        // presenting that ETag would get a 304.
        String scenario = "etag";
        wireMock.stubFor(get(urlPathMatching(FEATURES_PATH)).inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withHeader("ETag", "\"v1\"").withBody(FEATURES)
                        .withChunkedDribbleDelay(CHUNKS, SLOW_BODY_MILLIS))
                .willSetStateTo("fast"));
        wireMock.stubFor(get(urlPathMatching(FEATURES_PATH)).inScenario(scenario)
                .whenScenarioStateIs("fast")
                .withHeader("If-None-Match", equalTo("\"v1\""))
                .atPriority(1)
                .willReturn(aResponse().withStatus(304)));
        wireMock.stubFor(get(urlPathMatching(FEATURES_PATH)).inScenario(scenario)
                .whenScenarioStateIs("fast")
                .atPriority(2)
                .willReturn(aResponse().withStatus(200).withHeader("ETag", "\"v1\"").withBody(FEATURES)));
        GBFeaturesRepository subject = repository(REQUEST_TIMEOUT);
        assertThrows(FeatureFetchException.class, subject::fetchFeatures);

        // When
        subject.fetchFeatures();

        // Then: the features were fetched again rather than "not modified" into an empty state
        assertEquals(FEATURES_JSON, subject.getFeaturesJson());
        subject.shutdown();
    }

    private void stubSlowBody() {
        wireMock.stubFor(get(urlPathMatching(FEATURES_PATH))
                .willReturn(aResponse().withStatus(200).withBody(FEATURES)
                        .withChunkedDribbleDelay(CHUNKS, SLOW_BODY_MILLIS)));
    }

    private GBFeaturesRepository repository(Duration requestTimeout) {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .retryPolicy(SINGLE_ATTEMPT)
                .requestTimeout(requestTimeout)
                .build();
    }
}
