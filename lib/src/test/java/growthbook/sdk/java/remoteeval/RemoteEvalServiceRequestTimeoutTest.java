package growthbook.sdk.java.remoteeval;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Verifies that {@link RemoteEvalService} applies a configured {@code requestTimeout} to the client it
 * creates, so a remote-eval call against a stalling endpoint fails fast, and that an invalid timeout
 * is rejected up front.
 */
class RemoteEvalServiceRequestTimeoutTest {

    private static final String CLIENT_KEY = "sdk-remote-eval-timeout";
    private static final String EVAL_PATH = RemoteEvalEndpoints.EVAL_PATH_PREFIX + CLIENT_KEY;

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlPathEqualTo(EVAL_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10_000)
                        .withBody("{\"features\":{}}")));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @DisplayName("Verify: a configured requestTimeout fails a remote-eval fetch fast when the endpoint stalls")
    void failsFastWhenEndpointStalls() {
        RemoteEvalService service = new RemoteEvalService(
                "http://localhost:" + wireMock.port(), CLIENT_KEY, Duration.ofMillis(300));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(FeatureFetchException.class, () -> service.fetch(new RequestBodyForRemoteEval())));
        } finally {
            service.close();
        }
    }

    @Test
    @DisplayName("Verify: a negative requestTimeout is rejected at construction")
    void rejectsNegativeRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new RemoteEvalService("https://cdn.growthbook.io", CLIENT_KEY, Duration.ofMillis(-1)));
    }
}
