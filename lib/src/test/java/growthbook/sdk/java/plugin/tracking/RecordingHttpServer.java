package growthbook.sdk.java.plugin.tracking;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny dependency-free HTTP server for tests, backed by the JDK's
 * {@link HttpServer}. Records received requests and replies with queued status
 * codes (default 200). Used instead of OkHttp's MockWebServer so the plugin
 * HTTP tests don't couple to okhttp internals on the test classpath.
 */
final class RecordingHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
    private final Queue<Integer> responseCodes = new ConcurrentLinkedQueue<>();
    private final Queue<Long> responseDelaysMs = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    RecordingHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String body = new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
                Map<String, String> headers = new HashMap<>();
                for (Map.Entry<String, List<String>> e : exchange.getRequestHeaders().entrySet()) {
                    if (e.getValue() != null && !e.getValue().isEmpty()) {
                        // Store keys lowercased so lookups are case-insensitive.
                        headers.put(e.getKey().toLowerCase(), e.getValue().get(0));
                    }
                }
                requestCount.incrementAndGet();
                requests.add(new RecordedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        headers,
                        body));

                Long delay = responseDelaysMs.poll();
                if (delay != null && delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Integer code = responseCodes.poll();
                exchange.sendResponseHeaders(code != null ? code : 200, -1);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool(daemonFactory()));
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void enqueue(int statusCode) {
        enqueue(statusCode, 0);
    }

    /** Queue a response that the server holds for {@code delayMs} before replying. */
    void enqueue(int statusCode, long delayMs) {
        responseCodes.add(statusCode);
        responseDelaysMs.add(delayMs);
    }

    RecordedRequest takeRequest(long timeout, TimeUnit unit) throws InterruptedException {
        return requests.poll(timeout, unit);
    }

    int getRequestCount() {
        return requestCount.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "recording-http-server");
            thread.setDaemon(true);
            return thread;
        };
    }

    static final class RecordedRequest {
        private final String method;
        private final String path;
        private final Map<String, String> headers;
        private final String body;

        RecordedRequest(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        String getMethod() {
            return method;
        }

        String getPath() {
            return path;
        }

        String getHeader(String name) {
            return headers.get(name.toLowerCase());
        }

        String bodyUtf8() {
            return body;
        }
    }
}
