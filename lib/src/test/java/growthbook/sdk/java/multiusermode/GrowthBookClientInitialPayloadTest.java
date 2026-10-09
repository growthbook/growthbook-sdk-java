package growthbook.sdk.java.multiusermode;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

/**
 * Client-facing behaviour of the inline bootstrap payload: an offline cold start that still evaluates
 * from the seeded snapshot, and a fail-fast on a malformed payload. The "unreachable API" is modelled
 * with a WireMock server that always errors, so no real network is involved.
 */
class GrowthBookClientInitialPayloadTest {

    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        // Stand-in for an unreachable API: the features endpoint always errors, so the SDK must fall
        // back to the seeded payload for evaluation.
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse().withStatus(500)));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @DisplayName("Offline: initialize() returns true and isOn() evaluates from the seeded payload")
    void offlineClient_evaluatesFromInitialPayload() {
        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        boolean initialized = client.initialize();

        assertTrue(initialized, "client should be ready from the seeded payload even while offline");
        assertTrue(client.isOn("my-flag", UserContext.builder().build()));

        client.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("Offline SSE: a seeded start does not block initialize() on the network and serves the seed")
    void seededSseClient_initializesWithoutBlocking() {
        // The features endpoint hangs well past the test timeout: a blocking SSE startup would stall
        // initialize() and trip @Timeout, so completing quickly proves the seeded startup runs off-thread.
        wireMock.resetAll();
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(60_000)
                        .withBody("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")));

        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        assertTrue(client.initialize(), "seeded SSE client should be ready from the seed without waiting on the network");
        assertTrue(client.isOn("my-flag", UserContext.builder().build()));

        client.shutdown();
    }

    @Test
    @DisplayName("Malformed payload: initialize() reports failure instead of silently starting empty")
    void malformedPayload_reportsStartupFailure() {
        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{ not valid json")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        assertFalse(client.initialize());
        client.shutdown();
    }
}
