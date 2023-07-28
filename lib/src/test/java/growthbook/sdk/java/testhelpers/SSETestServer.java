package growthbook.sdk.java.testhelpers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.FeatureFetchException;
import growthbook.sdk.java.FeatureRefreshStrategy;
import growthbook.sdk.java.GBFeaturesRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Run this to test an actual SSE implementation end-to-end.
 */
public class SSETestServer {
    public static void main(String[] args) throws IOException, FeatureFetchException {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
            .apiHost("https://cdn.growthbook.io")
            .clientKey("sdk-pGmC6LrsiUoEUcpZ")
            .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
            .build();

        subject.initialize();

        System.out.println("SSE Test server: http://localhost:8081/ping");

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/ping", new TestServerHandler());
        server.setExecutor(null);
        server.start();
    }

    private static class TestServerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "pong";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
