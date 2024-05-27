package growthbook.sdk.java.testhelpers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.FeatureFetchException;
import growthbook.sdk.java.FeatureRefreshStrategy;
import growthbook.sdk.java.GBContext;
import growthbook.sdk.java.GBFeaturesRepository;
import growthbook.sdk.java.GrowthBook;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Run this to test an actual SSE implementation end-to-end.
 */
public class SSETestServer {
    public static void main(String[] args) throws IOException, FeatureFetchException {
        // Unencrypted
        GBFeaturesRepository featuresRepository = GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-pGmC6LrsiUoEUcpZ")
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build();

        // Encrypted
//        GBFeaturesRepository featuresRepository = GBFeaturesRepository.builder()
//            .apiHost("https://cdn.growthbook.io")
//            .clientKey("sdk-862b5mHcP9XPugqD")
//            .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
//            .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
//            .build();

        featuresRepository.initialize();

        System.out.println("SSE Test server: http://localhost:8081/ping");

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/ping", new TestServerHandler(featuresRepository));
        server.setExecutor(null);
        server.start();
    }

    private static class TestServerHandler implements HttpHandler {
        private final GBFeaturesRepository featuresRepository;

        TestServerHandler(GBFeaturesRepository featuresRepository) {
            this.featuresRepository = featuresRepository;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Setup GrowthBook SDK
            GBContext context = GBContext.builder()
                    .featuresJson(featuresRepository.getFeaturesJson())
                    .build();
            GrowthBook growthBook = new GrowthBook(context);

            // Get a feature value
            String randomString = growthBook.getFeatureValue("greeting", "????");

            // Create a response
            String response = "pong: ";
            response += randomString;

            // Send response
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }
}
