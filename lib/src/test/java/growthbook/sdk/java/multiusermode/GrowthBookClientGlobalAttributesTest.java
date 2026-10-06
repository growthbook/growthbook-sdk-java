package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookClientGlobalAttributesTest {

    private static final String CLIENT_KEY = "sdk-test";

    private static final String COMBO_FEATURE =
            "{\"features\":{\"combo\":{\"defaultValue\":false,"
                    + "\"rules\":[{\"condition\":{\"id\":\"1\",\"plan\":\"pro\"},\"force\":true}]}}}";

    @Test
    @DisplayName("setGlobalAttributes followed by updateGlobalAttributes exposes both attributes to evaluation")
    void setThenUpdateEvaluationSeesBothAttributes() throws Exception {
        HttpServer server = startFeatureServer(COMBO_FEATURE);
        GrowthBookClient client = null;
        try {
            client = new GrowthBookClient(Options.builder()
                    .apiHost(apiHost(server))
                    .clientKey(CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build());
            assertTrue(client.initialize());

            UserContext user = UserContext.builder().build();

            client.setGlobalAttributes("{\"id\":\"1\"}");
            assertFalse(client.isOn("combo", user));

            client.updateGlobalAttributes("{\"plan\":\"pro\"}");
            assertTrue(client.isOn("combo", user));
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    @Test
    @DisplayName("updateGlobalAttributes preserves previously set keys (no silent loss)")
    void updatePreservesPreviouslySetKeys() throws Exception {
        HttpServer server = startFeatureServer(COMBO_FEATURE);
        GrowthBookClient client = null;
        try {
            client = new GrowthBookClient(Options.builder()
                    .apiHost(apiHost(server))
                    .clientKey(CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build());
            assertTrue(client.initialize());

            client.setGlobalAttributes("{\"id\":\"1\",\"plan\":\"pro\"}");
            client.updateGlobalAttributes("{\"country\":\"UA\"}");

            assertTrue(client.isOn("combo", UserContext.builder().build()));
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    @Test
    @DisplayName("In remote-eval mode, updateGlobalAttributes invalidates the response cache so the next evaluation refetches")
    void remoteEvalUpdateInvalidatesCache() throws Exception {
        try (EvalServer server = new EvalServer()) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey(CLIENT_KEY)
                    .remoteEval(true)
                    .cacheKeyAttributes(Collections.singletonList("id"))
                    .build());
            assertTrue(client.initialize());
            try {
                UserContext user = UserContext.builder()
                        .attributes(GrowthBookJsonUtils.getInstance().gson
                                .fromJson("{\"id\":\"1\"}", JsonObject.class))
                        .build();

                assertTrue(client.isOn("remote-feature", user));
                client.isOn("remote-feature", user);
                assertEquals(1, server.evalCount());

                client.updateGlobalAttributes("{\"plan\":\"pro\"}");
                client.isOn("remote-feature", user);
                assertEquals(2, server.evalCount());

                String body = server.lastRequestBody();
                assertTrue(body.contains("\"id\"") && body.contains("\"1\""), body);
                assertTrue(body.contains("\"plan\"") && body.contains("\"pro\""), body);
            } finally {
                client.shutdown();
            }
        }
    }

    private static HttpServer startFeatureServer(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/" + CLIENT_KEY, exchange -> {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.X_SSE_SUPPORT.getHeader(), "enabled");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(responseBytes);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String apiHost(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static final class EvalServer implements AutoCloseable {
        private static final String FEATURES =
                "{\"features\":{\"remote-feature\":{\"defaultValue\":true}},\"savedGroups\":{}}";

        private final HttpServer server;
        private final AtomicInteger evalCount = new AtomicInteger();
        private final AtomicReference<String> lastBody = new AtomicReference<>("");

        EvalServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/api/eval/" + CLIENT_KEY, this::handleEval);
            this.server.start();
        }

        String apiHost() {
            return "http://127.0.0.1:" + this.server.getAddress().getPort();
        }

        int evalCount() {
            return this.evalCount.get();
        }

        String lastRequestBody() {
            return this.lastBody.get();
        }

        private void handleEval(HttpExchange exchange) throws IOException {
            evalCount.incrementAndGet();
            this.lastBody.set(readBody(exchange));
            byte[] body = FEATURES.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int read;
                while ((read = is.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        }

        @Override
        public void close() {
            this.server.stop(0);
        }
    }
}
