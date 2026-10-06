package growthbook.sdk.java.remoteeval;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link RemoteEvalService} sends custom request headers
 * ({@code apiHostRequestHeaders}) and the SDK User-Agent on the remote
 * evaluation POST, capturing the headers actually sent on the wire.
 */
class RemoteEvalServiceCustomHeadersTest {

    private static final String TEST_CLIENT_KEY = "sdk-remote-eval-headers";
    private static final String EVAL_BODY = "{\"features\":{\"test-feature\":{\"defaultValue\":true}}}";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("Verify: custom headers and SDK User-Agent are sent on the remote evaluation request")
    void remoteEvalRequestCarriesCustomHeaders() throws Exception {
        AtomicReference<Headers> captured = new AtomicReference<>();
        String apiHost = startServer(captured);

        Map<String, String> customHeaders = new LinkedHashMap<>();
        customHeaders.put("Authorization", "Bearer remote-eval-token");
        customHeaders.put("X-Gateway-Key", "gateway-value");

        RemoteEvalService service = new RemoteEvalService(apiHost, TEST_CLIENT_KEY, customHeaders);
        try {
            RemoteEvalResponse response = service.fetch(new RequestBodyForRemoteEval());
            assertNotNull(response);
        } finally {
            service.close();
        }

        Headers headers = captured.get();
        assertNotNull(headers, "The eval endpoint should have been called");
        assertEquals("Bearer remote-eval-token", headers.getFirst("Authorization"));
        assertEquals("gateway-value", headers.getFirst("X-Gateway-Key"));
        String userAgent = headers.getFirst("User-Agent");
        assertNotNull(userAgent);
        assertTrue(userAgent.startsWith("growthbook-sdk-java/"), userAgent);
    }

    @Test
    @DisplayName("Verify: a custom User-Agent is overridden by the SDK User-Agent")
    void sdkUserAgentWinsOverCustomHeader() throws Exception {
        AtomicReference<Headers> captured = new AtomicReference<>();
        String apiHost = startServer(captured);

        RemoteEvalService service = new RemoteEvalService(
                apiHost,
                TEST_CLIENT_KEY,
                java.util.Collections.singletonMap("User-Agent", "custom-agent/1.0")
        );
        try {
            service.fetch(new RequestBodyForRemoteEval());
        } finally {
            service.close();
        }

        Headers headers = captured.get();
        assertNotNull(headers, "The eval endpoint should have been called");
        assertTrue(headers.getFirst("User-Agent").startsWith("growthbook-sdk-java/"),
                headers.getFirst("User-Agent"));
    }

    private String startServer(AtomicReference<Headers> capturedHeaders) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(RemoteEvalEndpoints.EVAL_PATH_PREFIX + TEST_CLIENT_KEY, exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            byte[] body = EVAL_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
