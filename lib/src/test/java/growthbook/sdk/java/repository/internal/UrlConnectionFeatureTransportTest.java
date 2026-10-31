package growthbook.sdk.java.repository.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlConnectionFeatureTransportTest {

    private static final String USER_AGENT = "growthbook-sdk-java/" + Version.SDK_VERSION;

    private WireMockServer wireMock;
    private UrlConnectionFeatureTransport transport;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        transport = new UrlConnectionFeatureTransport();
    }

    @AfterEach
    void tearDown() {
        transport.closeEvents();
        wireMock.stop();
    }

    @Test
    @DisplayName("Sends the given headers and the SDK User-Agent on a GET")
    void sendsGivenHeadersAndUserAgentOnGet() throws IOException {
        // Given
        wireMock.stubFor(get(urlEqualTo("/api/features/sdk-test")).willReturn(aResponse().withStatus(200).withBody("{}")));
        Map<String, String> headers = new HashMap<>();
        headers.put("If-None-Match", "\"etag-1\"");
        headers.put("X-Custom", "value-1");

        // When
        transport.get(url("/api/features/sdk-test"), headers);

        // Then
        wireMock.verify(getRequestedFor(urlEqualTo("/api/features/sdk-test"))
                .withHeader("If-None-Match", equalTo("\"etag-1\""))
                .withHeader("X-Custom", equalTo("value-1"))
                .withHeader("User-Agent", equalTo(USER_AGENT)));
    }

    @Test
    @DisplayName("Returns the status, reason phrase, body and headers of a GET")
    void returnsStatusMessageBodyAndHeadersOfGet() throws IOException {
        // Given
        wireMock.stubFor(get(urlEqualTo("/api/features/sdk-test")).willReturn(aResponse()
                .withStatus(200)
                .withStatusMessage("Fine")
                .withHeader("ETag", "\"etag-1\"")
                .withBody("{\"features\":{}}")));

        // When
        TransportResponse response = transport.get(url("/api/features/sdk-test"), Collections.<String, String>emptyMap());

        // Then
        assertEquals(200, response.code());
        assertEquals("Fine", response.message());
        assertEquals("{\"features\":{}}", response.body());
        assertEquals("\"etag-1\"", response.header("etag"));
    }

    @Test
    @DisplayName("Reads a non-ASCII UTF-8 body exactly")
    void readsUtf8BodyExactly() throws IOException {
        // Given
        String body = "{\"greeting\":\"héllo ✓\"}";
        wireMock.stubFor(get(urlEqualTo("/api/features/sdk-test")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body.getBytes(StandardCharsets.UTF_8))));

        // When
        TransportResponse response = transport.get(url("/api/features/sdk-test"), Collections.<String, String>emptyMap());

        // Then
        assertEquals(body, response.body());
    }

    @Test
    @DisplayName("Returns the error body of an error status")
    void returnsErrorBodyOfErrorStatus() throws IOException {
        // Given
        wireMock.stubFor(get(urlEqualTo("/api/features/sdk-test")).willReturn(aResponse()
                .withStatus(500)
                .withBody("{\"error\":\"server-error\"}")));

        // When
        TransportResponse response = transport.get(url("/api/features/sdk-test"), Collections.<String, String>emptyMap());

        // Then
        assertEquals(500, response.code());
        assertEquals("{\"error\":\"server-error\"}", response.body());
    }

    @Test
    @DisplayName("Returns a 304 without a body")
    void returnsNotModifiedWithoutBody() throws IOException {
        // Given
        wireMock.stubFor(get(urlEqualTo("/api/features/sdk-test")).willReturn(aResponse().withStatus(304)));

        // When
        TransportResponse response = transport.get(url("/api/features/sdk-test"), Collections.singletonMap("If-None-Match", "\"etag-1\""));

        // Then
        assertEquals(304, response.code());
        assertTrue(response.body() == null || response.body().isEmpty());
    }

    @Test
    @DisplayName("Posts the JSON body as UTF-8 with a JSON content type and the SDK User-Agent")
    void postsJsonBodyAsUtf8() throws IOException {
        // Given
        String json = "{\"attributes\":{\"id\":\"user-1\",\"label\":\"héllo ✓\"}}";
        wireMock.stubFor(post(urlEqualTo("/api/eval/sdk-test")).willReturn(aResponse().withStatus(200).withBody("{\"features\":{}}")));

        // When
        TransportResponse response = transport.postJson(url("/api/eval/sdk-test"), json);

        // Then
        assertEquals(200, response.code());
        assertEquals("{\"features\":{}}", response.body());
        wireMock.verify(postRequestedFor(urlEqualTo("/api/eval/sdk-test"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("User-Agent", equalTo(USER_AGENT))
                .withRequestBody(binaryEqualTo(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("Fails a GET with an exception when no server is listening")
    void failsGetWhenNoServerListens() throws IOException {
        // Given
        String unreachable = "http://127.0.0.1:" + unusedPort() + "/api/features/sdk-test";

        // When
        // Then
        assertThrows(ConnectException.class, () -> transport.get(unreachable, Collections.<String, String>emptyMap()));
    }

    @Test
    @DisplayName("Fails a JSON POST with an exception when no server is listening")
    void failsPostWhenNoServerListens() throws IOException {
        // Given
        String unreachable = "http://127.0.0.1:" + unusedPort() + "/api/eval/sdk-test";

        // When
        // Then
        assertThrows(ConnectException.class, () -> transport.postJson(unreachable, "{}"));
    }

    @Test
    @Timeout(10)
    @DisplayName("Opens the stream, parses events per the server-sent events format and reports the end of the stream")
    void parsesEventsAndReportsEndOfStream() throws Exception {
        // Given
        String stream = ": keep-alive comment\n"
                + "event: features\n"
                + "data: {\"a\":1}\n"
                + "data: line-2\n"
                + "\n"
                + "data:no-space\n"
                + "\n"
                + "event: orphan\n"
                + "\n"
                + "data:  two-spaces\n"
                + "\n";
        RecordingListener listener = new RecordingListener(3);

        try (EventServer server = new EventServer(200, stream)) {
            // When
            transport.openEvents(server.url(), listener);

            // Then
            assertTrue(listener.closed.await(5, TimeUnit.SECONDS));
            assertEquals(1, listener.openCount.get());
            assertEquals(Arrays.asList(
                    Arrays.asList("features", "{\"a\":1}\nline-2"),
                    Arrays.asList(null, "no-space"),
                    Arrays.asList(null, " two-spaces")), listener.events);
            assertEquals(0, listener.failureCount.get());
            assertEquals(USER_AGENT, server.userAgent.get());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Dispatches nothing for an event type that has no data")
    void dispatchesNothingForEventWithoutData() throws Exception {
        // Given
        String stream = "event: features\n\n";
        RecordingListener listener = new RecordingListener(1);

        try (EventServer server = new EventServer(200, stream)) {
            // When
            transport.openEvents(server.url(), listener);

            // Then
            assertTrue(listener.closed.await(5, TimeUnit.SECONDS));
            assertTrue(listener.events.isEmpty());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Drops an event the stream ends before dispatching")
    void dropsEventNotTerminatedByBlankLine() throws Exception {
        // Given
        String stream = "data: first\n\ndata: unfinished\n";
        RecordingListener listener = new RecordingListener(1);

        try (EventServer server = new EventServer(200, stream)) {
            // When
            transport.openEvents(server.url(), listener);

            // Then
            assertTrue(listener.closed.await(5, TimeUnit.SECONDS));
            assertEquals(Collections.singletonList(Arrays.asList(null, "first")), listener.events);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Reports a failure and does not open on a non-200 status")
    void reportsFailureOnNon200Status() throws Exception {
        // Given
        RecordingListener listener = new RecordingListener(1);

        try (EventServer server = new EventServer(503, "")) {
            // When
            transport.openEvents(server.url(), listener);

            // Then
            assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, listener.failure.get());
            assertEquals(0, listener.openCount.get());
            assertEquals(0, listener.closedCount.get());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Reports a failure when no server is listening for events")
    void reportsFailureWhenNoEventServerListens() throws Exception {
        // Given
        RecordingListener listener = new RecordingListener(1);
        String unreachable = "http://127.0.0.1:" + unusedPort() + "/sub/sdk-test";

        // When
        transport.openEvents(unreachable, listener);

        // Then
        assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
        assertInstanceOf(ConnectException.class, listener.failure.get());
        assertEquals(0, listener.openCount.get());
    }

    @Test
    @Timeout(10)
    @DisplayName("Reads events on a daemon thread")
    void readsEventsOnDaemonThread() throws Exception {
        // Given
        RecordingListener listener = new RecordingListener(1);

        try (EventServer server = new EventServer(200, "data: first\n\n")) {
            // When
            transport.openEvents(server.url(), listener);

            // Then
            assertTrue(listener.closed.await(5, TimeUnit.SECONDS));
            assertTrue(listener.readerThread.get().isDaemon());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Cancels an idle stream without blocking and without calling the listener again")
    void cancelsIdleStreamWithoutBlocking() throws Exception {
        // Given
        CountDownLatch release = new CountDownLatch(1);
        RecordingListener listener = new RecordingListener(1);
        try (EventServer server = new EventServer(200, "data: first\n\n", release)) {
            FeatureTransport.SseConnection connection = transport.openEvents(server.url(), listener);
            assertTrue(listener.received.await(5, TimeUnit.SECONDS));

            // When
            connection.cancel();
            release.countDown();
            listener.readerThread.get().join(TimeUnit.SECONDS.toMillis(5));

            // Then
            assertEquals(0, listener.closedCount.get());
            assertEquals(0, listener.failureCount.get());
            assertEquals(1, listener.events.size());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Closes every idle stream without blocking")
    void closesIdleStreamsWithoutBlocking() throws Exception {
        // Given
        CountDownLatch release = new CountDownLatch(1);
        RecordingListener listener = new RecordingListener(1);
        try (EventServer server = new EventServer(200, "data: first\n\n", release)) {
            transport.openEvents(server.url(), listener);
            assertTrue(listener.received.await(5, TimeUnit.SECONDS));

            // When
            transport.closeEvents();
            release.countDown();
            listener.readerThread.get().join(TimeUnit.SECONDS.toMillis(5));

            // Then
            assertEquals(0, listener.closedCount.get());
            assertEquals(0, listener.failureCount.get());
        }
    }

    private String url(String path) {
        return "http://localhost:" + wireMock.port() + path;
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class RecordingListener implements FeatureTransport.SseListener {
        private final List<List<String>> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicInteger closedCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<Thread> readerThread = new AtomicReference<>();
        private final CountDownLatch received;
        private final CountDownLatch closed = new CountDownLatch(1);
        private final CountDownLatch failed = new CountDownLatch(1);

        RecordingListener(int expectedEvents) {
            this.received = new CountDownLatch(expectedEvents);
        }

        @Override
        public void onOpen() {
            readerThread.set(Thread.currentThread());
            openCount.incrementAndGet();
        }

        @Override
        public void onEvent(@Nullable String type, String data) {
            events.add(Arrays.asList(type, data));
            received.countDown();
        }

        @Override
        public void onClosed() {
            closedCount.incrementAndGet();
            closed.countDown();
        }

        @Override
        public void onFailure(@Nullable Throwable error) {
            failure.set(error);
            failureCount.incrementAndGet();
            failed.countDown();
        }
    }

    private static final class EventServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final AtomicReference<String> userAgent = new AtomicReference<>();
        private final int status;
        private final String stream;
        @Nullable
        private final CountDownLatch holdOpenUntil;

        EventServer(int status, String stream) throws IOException {
            this(status, stream, null);
        }

        EventServer(int status, String stream, @Nullable CountDownLatch holdOpenUntil) throws IOException {
            this.status = status;
            this.stream = stream;
            this.holdOpenUntil = holdOpenUntil;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/sub/sdk-test", this::handle);
            this.server.setExecutor(executor);
            this.server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/sub/sdk-test";
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (InputStream in = exchange.getRequestBody()) {
                byte[] buffer = new byte[1024];
                while (in.read(buffer) != -1) {
                }
            }
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            if (status != 200) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(stream.getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (holdOpenUntil != null) {
                    holdOpenUntil.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
