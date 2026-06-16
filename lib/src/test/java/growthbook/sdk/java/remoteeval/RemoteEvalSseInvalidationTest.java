package growthbook.sdk.java.remoteeval;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteEvalSseInvalidationTest {

    private static final String FEATURES = "{\"features\":{\"remote-feature\":{\"defaultValue\":true}},\"savedGroups\":{}}";

    @Test
    @DisplayName("An SSE invalidation event clears the remote eval cache so the next evaluation refetches")
    void sseEventInvalidatesRemoteEvalCacheAndNextEvaluationRefetches() throws Exception {
        try (SseServer server = new SseServer()) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                    .build());
            assertTrue(client.initialize());

            try {
                UserContext user = UserContext.builder()
                        .attributes(GrowthBookJsonUtils.getInstance().gson.fromJson("{\"id\":\"user-1\"}", JsonObject.class))
                        .build();

                assertTrue(server.awaitConnected(2000));
                assertTrue(client.isOn("remote-feature", user));
                client.isOn("remote-feature", user);
                assertEquals(1, server.evalCount());

                server.sendInvalidationEvent();
                assertTrue(awaitRefetch(server, client, user), "Expected SSE invalidation to force a fresh remote eval");
            } finally {
                client.shutdown();
            }
        }
    }

    private static boolean awaitRefetch(SseServer server, GrowthBookClient client, UserContext user) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            client.isOn("remote-feature", user);
            if (server.evalCount() >= 2) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    /** Minimal proxy: POST /api/eval, GET /api/features (advertising SSE), GET /sub (event stream). */
    private static final class SseServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicInteger evalCount = new AtomicInteger();
        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch sseGate = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);

        SseServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newCachedThreadPool();
            this.server.createContext("/api/eval/sdk-test", this::handleEval);
            this.server.createContext("/api/features/sdk-test", this::handleFeatures);
            this.server.createContext("/sub/sdk-test", this::handleSse);
            this.server.setExecutor(this.executor);
            this.server.start();
        }

        String apiHost() {
            return "http://127.0.0.1:" + this.server.getAddress().getPort();
        }

        int evalCount() {
            return this.evalCount.get();
        }

        boolean awaitConnected(long millis) throws InterruptedException {
            return connected.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        void sendInvalidationEvent() {
            sseGate.countDown();
        }

        private void handleEval(HttpExchange exchange) throws IOException {
            evalCount.incrementAndGet();
            drain(exchange);
            writeJson(exchange, FEATURES);
        }

        private void handleFeatures(HttpExchange exchange) throws IOException {
            drain(exchange);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Sse-Support", "enabled");
            byte[] body = FEATURES.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void handleSse(HttpExchange exchange) throws IOException {
            drain(exchange);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            connected.countDown();
            try {
                sseGate.await();
                os.write(("data: " + FEATURES + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                stop.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void writeJson(HttpExchange exchange, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void drain(HttpExchange exchange) throws IOException {
            try (java.io.InputStream is = exchange.getRequestBody()) {
                byte[] buffer = new byte[1024];
                while (is.read(buffer) != -1) {
                }
            }
        }

        @Override
        public void close() {
            stop.countDown();
            sseGate.countDown();
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }
}
