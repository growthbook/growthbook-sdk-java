package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards that starting server-sent events opens exactly one stream connection.
 */
class SseStreamSingleConnectionTest {

    private static final String FEATURES = "{\"features\":{\"flag\":{\"defaultValue\":true}},\"savedGroups\":{}}";

    @Test
    @DisplayName("Starting SSE submits exactly one call to the stream endpoint")
    void startingSseSubmitsExactlyOneStreamCall() throws Exception {
        // Given
        try (SseServer server = new SseServer()) {
            GBFeaturesRepository repository = GBFeaturesRepository.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                    .isCacheDisabled(true)
                    .build();

            try {
                // When
                repository.initialize();

                // Then
                Dispatcher dispatcher = readSseDispatcher(repository);
                assertEquals(1, dispatcher.runningCallsCount() + dispatcher.queuedCallsCount());
            } finally {
                repository.shutdown();
            }
        }
    }

    private static Dispatcher readSseDispatcher(GBFeaturesRepository repository) {
        OkHttpClient sseClient = RepositoryInternals.transport(repository).eventsClientIfCreated();
        assertNotNull(sseClient, "SSE client was never created; the stream was not started");
        return sseClient.dispatcher();
    }

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
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void handleSse(HttpExchange exchange) {
            try {
                drain(exchange);
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
                int read;
                do {
                    read = is.read(buffer);
                } while (read != -1);
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
