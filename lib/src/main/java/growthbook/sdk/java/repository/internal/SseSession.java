package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sse.SseEventPayloadValidator;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The server-sent events connection of one repository: opens it, hands feature payloads to the
 * {@link Handler}, and reconnects with the retry policy's backoff when it ends, on a daemon
 * scheduler. Heartbeats are ignored; an event without a usable payload signals that features
 * changed. Thread-safe.
 */
@Slf4j
public final class SseSession {

    private static final ThreadFactory RETRY_THREADS = runnable -> {
        Thread thread = new Thread(runnable, "growthbook-sse-retry");
        thread.setDaemon(true);
        return thread;
    };

    /**
     * What the session reports to its repository.
     */
    public interface Handler {
        void onFeatures(String featuresJson) throws FeatureFetchException;

        void onFeaturesChanged();

        /**
         * Runs before each reconnect, to catch up on what was missed while disconnected.
         */
        void beforeReconnect();
    }

    private final FeatureTransport transport;
    private final String eventsUrl;
    private final FeatureFetchRetryPolicy retryPolicy;
    private final Handler handler;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicInteger retryAttempts = new AtomicInteger();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<FeatureTransport.SseConnection> connection = new AtomicReference<>();
    private volatile boolean retryOnFailure;
    @Nullable
    private ScheduledExecutorService retryScheduler;

    public SseSession(FeatureTransport transport, String eventsUrl, FeatureFetchRetryPolicy retryPolicy, Handler handler) {
        this.transport = transport;
        this.eventsUrl = eventsUrl;
        this.retryPolicy = retryPolicy;
        this.handler = handler;
    }

    /**
     * @param retryOnFailure whether to reconnect when the connection ends
     */
    public void start(boolean retryOnFailure) {
        this.retryOnFailure = retryOnFailure;
        connect();
    }

    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    public int retryAttempts() {
        return retryAttempts.get();
    }

    /**
     * Closes the connection and stops reconnecting; the session can't be started again.
     */
    public synchronized void close() {
        closed.set(true);
        connected.set(false);
        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
            retryScheduler = null;
            log.info("SSE retry scheduler shut down");
        }
        FeatureTransport.SseConnection current = connection.getAndSet(null);
        if (current != null) {
            current.cancel();
            log.info("SseEventSource cancel");
        }
        transport.closeEvents();
    }

    private void connect() {
        connected.set(false);
        connection.set(transport.openEvents(eventsUrl, new Listener()));
    }

    synchronized void scheduleReconnect() {
        if (!retryOnFailure || closed.get() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        int nextAttempt = retryAttempts.incrementAndGet() + 1;
        int maxAttempts = retryPolicy.getMaxAttempts();
        if (nextAttempt > maxAttempts) {
            retryAttempts.set(maxAttempts);
            reconnectScheduled.set(false);
            log.error("SSE connection retries exhausted after {} attempts.", maxAttempts);
            return;
        }

        long delayMillis = retryPolicy.getDelayMillisBeforeAttempt(nextAttempt);
        log.warn("SSE connection failed. Retry attempt {}/{} in {}ms.", nextAttempt, maxAttempts, delayMillis);
        if (retryScheduler == null || retryScheduler.isShutdown()) {
            retryScheduler = Executors.newSingleThreadScheduledExecutor(RETRY_THREADS);
        }
        retryScheduler.schedule(this::reconnect, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void reconnect() {
        reconnectScheduled.set(false);
        if (closed.get()) {
            return;
        }
        handler.beforeReconnect();
        connect();
    }

    private void connectionLost() {
        connected.set(false);
        FeatureTransport.SseConnection current = connection.getAndSet(null);
        if (current != null) {
            current.cancel();
        }
        scheduleReconnect();
    }

    private final class Listener implements FeatureTransport.SseListener {
        @Override
        public void onOpen() {
            connected.set(true);
            retryAttempts.set(0);
            reconnectScheduled.set(false);
        }

        @Override
        public void onEvent(@Nullable String type, String data) {
            if (SseEventPayloadValidator.isHeartbeatEvent(type)) {
                return;
            }
            try {
                if (SseEventPayloadValidator.isValidFeaturePayload(type, data)) {
                    handler.onFeatures(data);
                    retryAttempts.set(0);
                    reconnectScheduled.set(false);
                } else {
                    handler.onFeaturesChanged();
                }
            } catch (FeatureFetchException e) {
                log.error("Failed to process SSE feature payload: {}", e.getMessage(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error while processing SSE feature payload.", e);
            }
        }

        @Override
        public void onClosed() {
            connectionLost();
        }

        @Override
        public void onFailure(@Nullable Throwable error) {
            if (error != null) {
                log.error("SSE connection failed: {}", error.getMessage(), error);
            }
            connectionLost();
        }
    }
}
