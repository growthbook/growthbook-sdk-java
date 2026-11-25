package growthbook.sdk.java.testhelpers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Manual end-to-end check of a real SSE connection, not a unit test: reads the client key from
 * {@code GROWTHBOOK_CLIENT_KEY} (and optionally the host from {@code GROWTHBOOK_API_HOST}) and serves
 * the current {@code greeting} feature value on {@code http://localhost:8081/ping}.
 */
public class SSETestServer {

    public static void main(String[] args) throws IOException, FeatureFetchException {
        String clientKey = System.getenv("GROWTHBOOK_CLIENT_KEY");
        if (clientKey == null || clientKey.trim().isEmpty()) {
            throw new IllegalStateException("Set GROWTHBOOK_CLIENT_KEY to run the SSE test server");
        }
        String apiHost = System.getenv("GROWTHBOOK_API_HOST");

        GBFeaturesRepository featuresRepository = GBFeaturesRepository.builder()
                .apiHost(apiHost == null || apiHost.trim().isEmpty() ? "https://cdn.growthbook.io" : apiHost)
                .clientKey(clientKey)
                .encryptionKey(System.getenv("GROWTHBOOK_DECRYPTION_KEY"))
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build();
        featuresRepository.initialize();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8081), 0);
        server.createContext("/ping", new TestServerHandler(featuresRepository));
        server.setExecutor(null);
        server.start();
    }

    private static final class TestServerHandler implements HttpHandler {
        private final GBFeaturesRepository featuresRepository;

        TestServerHandler(GBFeaturesRepository featuresRepository) {
            this.featuresRepository = featuresRepository;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            GrowthBook growthBook = new GrowthBook(GBContext.builder()
                    .featuresJson(featuresRepository.getFeaturesJson())
                    .build());
            byte[] response = ("pong: " + growthBook.getFeatureValue("greeting", "????")).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
