package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the SSE connection contract: starting the server-sent-events strategy must open exactly one
 * connection to the stream endpoint. A duplicate {@code newCall(sseRequest).enqueue(...)} alongside
 * the {@code EventSource} would open a second, immediately-closed stream connection per (re)connect;
 * this test fails if that duplicate is ever restored.
 */
class SseStreamSingleConnectionTest {

    private static final String FEATURES = "{\"features\":{\"flag\":{\"defaultValue\":true}},\"savedGroups\":{}}";

    @Test
    @DisplayName("Verify: starting SSE opens exactly one connection to the stream endpoint")
    void startingSseOpensExactlyOneStreamConnection() throws Exception {
        // Given a proxy that advertises SSE support and counts stream connections
        try (SseServer server = new SseServer()) {
            GBFeaturesRepository repository = GBFeaturesRepository.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                    .isCacheDisabled(true)
                    .build();

            try {
                // When the repository initializes and establishes the stream
                repository.initialize();
                assertTrue(server.awaitFirstConnection(2000), "SSE stream was never opened");

                // Then no second connection arrives within the window a duplicate request would use
                assertFalse(
                        server.awaitSecondConnection(500),
                        "SSE must open exactly one stream connection, but a duplicate request was observed"
                );
                assertEquals(1, server.streamConnectionCount());
            } finally {
                repository.shutdown();
            }
        }
    }

    /** Minimal proxy: GET /api/features (advertising SSE) and GET /sub (event stream, counted). */
    private static final class SseServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicInteger streamConnections = new AtomicInteger();
        private final CountDownLatch firstConnection = new CountDownLatch(1);
        private final CountDownLatch secondConnection = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);

        SseServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newCachedThreadPool();
            this.server.createContext("/api/features/sdk-test", this::handleFeatures);
            this.server.createContext("/sub/sdk-test", this::handleSse);
            this.server.setExecutor(this.executor);
            this.server.start();
        }

        String apiHost() {
            return "http://127.0.0.1:" + this.server.getAddress().getPort();
        }

        int streamConnectionCount() {
            return this.streamConnections.get();
        }

        boolean awaitFirstConnection(long millis) throws InterruptedException {
            return firstConnection.await(millis, TimeUnit.MILLISECONDS);
        }

        boolean awaitSecondConnection(long millis) throws InterruptedException {
            return secondConnection.await(millis, TimeUnit.MILLISECONDS);
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
            int connection = streamConnections.incrementAndGet();
            firstConnection.countDown();
            if (connection >= 2) {
                secondConnection.countDown();
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            try {
                stop.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    os.close();
                } catch (IOException ignored) {
                    // stream already torn down by shutdown
                }
            }
        }

        private void drain(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                byte[] buffer = new byte[1024];
                while (is.read(buffer) != -1) {
                }
            }
        }

        @Override
        public void close() {
            stop.countDown();
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }
}
