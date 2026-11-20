package growthbook.sdk.java.repository.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.Version;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkHttpFeatureTransportTest {

    private static final String USER_AGENT = "growthbook-sdk-java/" + Version.SDK_VERSION;
    private static final String EVENTS_PATH = "/sub/sdk-test";

    private WireMockServer wireMock;
    private OkHttpFeatureTransport transport;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort().gzipDisabled(true));
        wireMock.start();
        transport = new OkHttpFeatureTransport(null);
    }

    @AfterEach
    void tearDown() {
        transport.closeEvents();
        wireMock.stop();
    }

    @Test
    @DisplayName("Builds a default client that retries on connection failure when none is given")
    void buildsDefaultClientThatRetriesWhenNoneGiven() {
        // Given
        // When
        OkHttpFeatureTransport created = new OkHttpFeatureTransport(null);

        // Then
        assertNotNull(created.httpClient());
        assertTrue(created.httpClient().retryOnConnectionFailure());
    }

    @Test
    @DisplayName("Sends the given headers and the SDK User-Agent on a GET with the default client")
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
    @DisplayName("Uses a caller-supplied client as is")
    void usesCallerSuppliedClientAsIs() throws IOException {
        // Given
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("X-Caller", "custom-client").build()))
                .build();
        OkHttpFeatureTransport custom = new OkHttpFeatureTransport(client);
        wireMock.stubFor(get(urlEqualTo("/api/features/sdk-test")).willReturn(aResponse().withStatus(200).withBody("{}")));

        // When
        custom.get(url("/api/features/sdk-test"), Collections.<String, String>emptyMap());

        // Then
        assertSame(client, custom.httpClient());
        wireMock.verify(getRequestedFor(urlEqualTo("/api/features/sdk-test"))
                .withHeader("X-Caller", equalTo("custom-client"))
                .withHeader("User-Agent", notMatching("growthbook-sdk-java/.*")));
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
    @DisplayName("Posts the JSON body as UTF-8 with a JSON content type")
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
                .withHeader("Content-Type", matching("application/json(; ?charset=(?i)utf-8)?"))
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
    @DisplayName("Has no events client before the first events connection")
    void hasNoEventsClientBeforeFirstConnection() {
        // Given
        OkHttpFeatureTransport fresh = new OkHttpFeatureTransport(null);

        // When
        OkHttpClient eventsClient = fresh.eventsClientIfCreated();

        // Then
        assertNull(eventsClient);
    }

    @Test
    @Timeout(10)
    @DisplayName("Delivers the opening, the events and the end of the stream to the listener")
    void deliversEventsToListener() throws Exception {
        // Given
        String stream = "event: features\ndata: {\"a\":1}\ndata: line-2\n\ndata: second\n\n";
        RecordingListener listener = new RecordingListener(2);
        stubEvents(200, stream);

        // When
        transport.openEvents(url(EVENTS_PATH), listener);

        // Then
        assertTrue(listener.closed.await(5, TimeUnit.SECONDS));
        assertEquals(1, listener.openCount.get());
        assertEquals(Arrays.asList(
                Arrays.asList("features", "{\"a\":1}\nline-2"),
                Arrays.asList(null, "second")), listener.events);
        assertEquals(0, listener.failureCount.get());
        wireMock.verify(getRequestedFor(urlEqualTo(EVENTS_PATH)).withHeader("User-Agent", equalTo(USER_AGENT)));
    }

    @Test
    @Timeout(10)
    @DisplayName("Reports a failure and does not open on a non-200 status")
    void reportsFailureOnNon200Status() throws Exception {
        // Given
        RecordingListener listener = new RecordingListener(1);
        stubEvents(503, "");

        // When
        transport.openEvents(url(EVENTS_PATH), listener);

        // Then
        assertTrue(listener.failed.await(5, TimeUnit.SECONDS));
        assertEquals(0, listener.openCount.get());
        assertEquals(0, listener.closedCount.get());
    }

    @Test
    @Timeout(10)
    @DisplayName("Creates the events client on the first events connection and releases it when events are closed")
    void createsEventsClientOnFirstConnectionAndReleasesItOnClose() throws Exception {
        // Given
        RecordingListener listener = new RecordingListener(1);
        stubEvents(200, "data: first\n\n");
        transport.openEvents(url(EVENTS_PATH), listener);
        OkHttpClient eventsClient = transport.eventsClientIfCreated();
        assertNotNull(eventsClient);
        assertTrue(listener.received.await(5, TimeUnit.SECONDS));

        // When
        transport.closeEvents();

        // Then
        assertNull(transport.eventsClientIfCreated());
        assertTrue(eventsClient.dispatcher().executorService().isShutdown());
    }

    @Test
    @Timeout(10)
    @DisplayName("Reuses one events client across events connections")
    void reusesEventsClientAcrossConnections() throws Exception {
        // Given
        RecordingListener first = new RecordingListener(1);
        RecordingListener second = new RecordingListener(1);
        stubEvents(200, "data: first\n\n");
        transport.openEvents(url(EVENTS_PATH), first);
        OkHttpClient eventsClient = transport.eventsClientIfCreated();

        // When
        transport.openEvents(url(EVENTS_PATH), second);

        // Then
        assertSame(eventsClient, transport.eventsClientIfCreated());
    }

    @Test
    @DisplayName("Closing events before any events connection does nothing")
    void closingEventsBeforeAnyConnectionDoesNothing() {
        // Given
        OkHttpFeatureTransport fresh = new OkHttpFeatureTransport(null);

        // When
        fresh.closeEvents();

        // Then
        assertNull(fresh.eventsClientIfCreated());
    }

    private String url(String path) {
        return "http://localhost:" + wireMock.port() + path;
    }

    private void stubEvents(int status, String stream) {
        wireMock.stubFor(get(urlEqualTo(EVENTS_PATH)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(stream)));
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
        private final CountDownLatch received;
        private final CountDownLatch closed = new CountDownLatch(1);
        private final CountDownLatch failed = new CountDownLatch(1);

        RecordingListener(int expectedEvents) {
            this.received = new CountDownLatch(expectedEvents);
        }

        @Override
        public void onOpen() {
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
            failureCount.incrementAndGet();
            failed.countDown();
        }
    }
}
