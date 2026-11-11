package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the SSE connection contract: starting the server-sent-events strategy must submit exactly
 * one call to the stream endpoint. A duplicate {@code newCall(sseRequest).enqueue(...)} alongside the
 * {@code EventSource} would open a second, immediately-closed stream connection per (re)connect; this
 * test fails if that duplicate is ever restored.
 *
 * <p>Both SSE calls are enqueued synchronously inside {@code initialize()}, so the assertion reads the
 * SSE client's OkHttp dispatcher directly — no real-time polling. The test double holds the stream
 * connection open so the submitted calls stay in flight and cannot settle before they are counted.
 */
class SseStreamSingleConnectionTest {

    private static final String FEATURES = "{\"features\":{\"flag\":{\"defaultValue\":true}},\"savedGroups\":{}}";

    @Test
    @DisplayName("Verify: starting SSE submits exactly one call to the stream endpoint")
    void startingSseSubmitsExactlyOneStreamCall() throws Exception {
        // Given a proxy that advertises SSE support and holds the stream connection open
        try (SseServer server = new SseServer()) {
            GBFeaturesRepository repository = GBFeaturesRepository.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                    .isCacheDisabled(true)
                    .build();

            try {
                // When the repository initializes and enqueues the stream call(s)
                repository.initialize();

                // Then exactly one SSE call is in flight on the SSE client's dispatcher
                Dispatcher dispatcher = readSseDispatcher(repository);
                int inFlight = dispatcher.runningCallsCount() + dispatcher.queuedCallsCount();
                assertEquals(
                        1,
                        inFlight,
                        "SSE must submit exactly one stream call, but a duplicate request was observed"
                );
            } finally {
                repository.shutdown();
            }
        }
    }

    private static Dispatcher readSseDispatcher(GBFeaturesRepository repository) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField("sseHttpClient");
        field.setAccessible(true);
        AtomicReference<?> clientRef = (AtomicReference<?>) field.get(repository);
        OkHttpClient sseClient = (OkHttpClient) clientRef.get();
        assertNotNull(sseClient, "SSE client was never created; the stream was not started");
        return sseClient.dispatcher();
    }

    /** Minimal proxy: GET /api/features (advertising SSE) and GET /sub (stream held open). */
    private static final class SseServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
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

        private void handleFeatures(HttpExchange exchange) throws IOException {
            drain(exchange);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Sse-Support", "enabled");
            byte[] body = FEATURES.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void handleSse(HttpExchange exchange) {
            try {
                drain(exchange);
                // Hold the connection open so the submitted call stays in flight until teardown.
                stop.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected failure draining SSE request", e);
            } finally {
                exchange.close();
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
