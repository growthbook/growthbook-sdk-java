package growthbook.sdk.java.remoteeval;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@link RemoteEvalService} sends custom request headers
 * ({@code apiHostRequestHeaders}) and the SDK User-Agent on the remote
 * evaluation POST, asserting the headers recorded by WireMock.
 */
class RemoteEvalServiceCustomHeadersTest {

    private static final String TEST_CLIENT_KEY = "sdk-remote-eval-headers";
    private static final String EVAL_PATH = RemoteEvalEndpoints.EVAL_PATH_PREFIX + TEST_CLIENT_KEY;
    private static final String EVAL_BODY = "{\"features\":{\"test-feature\":{\"defaultValue\":true}}}";

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlPathEqualTo(EVAL_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(EVAL_BODY)));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @DisplayName("Verify: custom headers and SDK User-Agent are sent on the remote evaluation request")
    void remoteEvalRequestCarriesCustomHeaders() throws Exception {
        Map<String, String> customHeaders = new LinkedHashMap<>();
        customHeaders.put("Authorization", "Bearer remote-eval-token");
        customHeaders.put("X-Gateway-Key", "gateway-value");

        RemoteEvalService service = new RemoteEvalService(apiHost(), TEST_CLIENT_KEY, customHeaders);
        try {
            RemoteEvalResponse response = service.fetch(new RequestBodyForRemoteEval());
            assertNotNull(response);
        } finally {
            service.close();
        }

        wireMock.verify(postRequestedFor(urlPathEqualTo(EVAL_PATH))
                .withHeader("Authorization", equalTo("Bearer remote-eval-token"))
                .withHeader("X-Gateway-Key", equalTo("gateway-value"))
                .withHeader("User-Agent", matching("growthbook-sdk-java/.*")));
    }

    @Test
    @DisplayName("Verify: a custom User-Agent is overridden by the SDK User-Agent")
    void sdkUserAgentWinsOverCustomHeader() throws Exception {
        RemoteEvalService service = new RemoteEvalService(
                apiHost(),
                TEST_CLIENT_KEY,
                Collections.singletonMap("User-Agent", "custom-agent/1.0"));
        try {
            service.fetch(new RequestBodyForRemoteEval());
        } finally {
            service.close();
        }

        wireMock.verify(postRequestedFor(urlPathEqualTo(EVAL_PATH))
                .withHeader("User-Agent", matching("growthbook-sdk-java/.*")));
    }

    private String apiHost() {
        return "http://localhost:" + wireMock.port();
    }
}
