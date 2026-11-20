package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseSessionTest {

    private static final FeatureFetchRetryPolicy SLOW_RETRIES = new FeatureFetchRetryPolicy(3, Duration.ofHours(1), Duration.ofHours(1));

    @Test
    @DisplayName("Does not reconnect when retrying on failure is off")
    void doesNotReconnectWhenRetryIsOff() {
        // Given
        FakeTransport transport = new FakeTransport();
        SseSession session = new SseSession(transport, "http://localhost/sub/sdk-1", SLOW_RETRIES, new RecordingHandler());
        session.start(false);

        // When
        transport.lastListener().onFailure(new IllegalStateException("connection lost"));

        // Then
        assertEquals(0, session.retryAttempts());
        assertEquals(1, transport.openCount());
        session.close();
    }

    @Test
    @DisplayName("Schedules a single reconnect per lost connection while one is pending")
    void schedulesOneReconnectWhilePending() {
        // Given
        FakeTransport transport = new FakeTransport();
        SseSession session = new SseSession(transport, "http://localhost/sub/sdk-1", SLOW_RETRIES, new RecordingHandler());
        session.start(true);

        // When
        transport.lastListener().onFailure(null);
        transport.lastListener().onClosed();

        // Then
        assertEquals(1, session.retryAttempts());
        session.close();
    }

    @Test
    @Timeout(10)
    @DisplayName("Stops reconnecting once the retry policy's attempts are used up")
    void stopsReconnectingAfterMaxAttempts() throws InterruptedException {
        // Given
        int maxAttempts = 3;
        FakeTransport transport = new FakeTransport();
        transport.failEveryConnection();
        RecordingHandler handler = new RecordingHandler();
        FeatureFetchRetryPolicy immediateRetries = new FeatureFetchRetryPolicy(maxAttempts, Duration.ZERO, Duration.ZERO);
        SseSession session = new SseSession(transport, "http://localhost/sub/sdk-1", immediateRetries, handler);

        // When
        session.start(true);
        assertTrue(transport.awaitOpens(maxAttempts));

        // Then
        assertEquals(maxAttempts, session.retryAttempts());
        assertEquals(maxAttempts - 1, handler.reconnects.get());
        session.close();
    }

    @Test
    @DisplayName("Hands a feature payload to the handler and resets the retry count")
    void handsPayloadToHandler() {
        // Given
        FakeTransport transport = new FakeTransport();
        RecordingHandler handler = new RecordingHandler();
        SseSession session = new SseSession(transport, "http://localhost/sub/sdk-1", SLOW_RETRIES, handler);
        session.start(true);
        transport.lastListener().onFailure(null);

        // When
        transport.lastListener().onEvent("features", "{\"features\":{}}");

        // Then
        assertEquals(1, handler.payloads.size());
        assertEquals(0, session.retryAttempts());
        session.close();
    }

    @Test
    @DisplayName("Ignores heartbeats and reports an event without a payload as a change")
    void ignoresHeartbeatsAndReportsEmptyEventsAsChanges() {
        // Given
        FakeTransport transport = new FakeTransport();
        RecordingHandler handler = new RecordingHandler();
        SseSession session = new SseSession(transport, "http://localhost/sub/sdk-1", SLOW_RETRIES, handler);
        session.start(true);

        // When
        transport.lastListener().onEvent("ping", "");
        transport.lastListener().onEvent("features-updated", "");

        // Then
        assertTrue(handler.payloads.isEmpty());
        assertEquals(1, handler.changes.get());
        session.close();
    }

    @Test
    @DisplayName("Is connected after the stream opens and not after close")
    void reportsConnectionState() {
        // Given
        FakeTransport transport = new FakeTransport();
        SseSession session = new SseSession(transport, "http://localhost/sub/sdk-1", SLOW_RETRIES, new RecordingHandler());
        session.start(true);

        // When
        transport.lastListener().onOpen();
        boolean connectedWhileOpen = session.isConnected();
        session.close();

        // Then
        assertTrue(connectedWhileOpen);
        assertFalse(session.isConnected());
        assertTrue(transport.eventsClosed);
    }

    private static final class FakeTransport implements FeatureTransport {
        private final List<SseListener> listeners = new CopyOnWriteArrayList<>();
        private final AtomicInteger opens = new AtomicInteger();
        private final List<CountDownLatch> openWaiters = new ArrayList<>();
        private volatile boolean failEveryConnection;
        private volatile boolean eventsClosed;

        void failEveryConnection() {
            failEveryConnection = true;
        }

        SseListener lastListener() {
            return listeners.get(listeners.size() - 1);
        }

        int openCount() {
            return opens.get();
        }

        boolean awaitOpens(int count) throws InterruptedException {
            CountDownLatch latch;
            synchronized (openWaiters) {
                latch = new CountDownLatch(Math.max(0, count - opens.get()));
                openWaiters.add(latch);
            }
            return latch.await(5, TimeUnit.SECONDS);
        }

        @Override
        public TransportResponse get(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransportResponse postJson(String url, String json) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SseConnection openEvents(String url, SseListener listener) {
            listeners.add(listener);
            if (failEveryConnection) {
                listener.onFailure(new IllegalStateException("refused"));
            }
            synchronized (openWaiters) {
                opens.incrementAndGet();
                openWaiters.forEach(CountDownLatch::countDown);
            }
            return () -> {
            };
        }

        @Override
        public void closeEvents() {
            eventsClosed = true;
        }
    }

    private static final class RecordingHandler implements SseSession.Handler {
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final AtomicInteger changes = new AtomicInteger();
        private final AtomicInteger reconnects = new AtomicInteger();

        @Override
        public void onFeatures(@Nullable String featuresJson) {
            payloads.add(featuresJson);
        }

        @Override
        public void onFeaturesChanged() {
            changes.incrementAndGet();
        }

        @Override
        public void beforeReconnect() {
            reconnects.incrementAndGet();
        }
    }
}
