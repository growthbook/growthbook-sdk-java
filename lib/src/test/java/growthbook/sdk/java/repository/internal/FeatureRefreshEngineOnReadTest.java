package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import growthbook.sdk.java.testhelpers.TestClock;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureRefreshEngineOnReadTest {

    private static final int TTL_SECONDS = 60;
    private static final FeatureEndpoints ENDPOINTS = FeatureEndpoints.of("http://localhost", "sdk-test");
    private static final String FEATURES_JSON = "{\"flag-a\":{\"defaultValue\":true}}";
    private static final String SAVED_GROUPS_JSON = "{\"group-1\":[\"user-1\"]}";
    private static final String BODY = "{\"features\":" + FEATURES_JSON + ",\"savedGroups\":" + SAVED_GROUPS_JSON + "}";

    private final FakeTransport transport = new FakeTransport();
    private final InMemoryCacheManager cacheManager = new InMemoryCacheManager();
    private final TestClock clock = new TestClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RecordingRefreshListener listener = new RecordingRefreshListener();

    @Test
    @DisplayName("Does not refresh on read before the TTL expired")
    void doesNotRefreshOnReadBeforeTtlExpired() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));
        clock.advance(Duration.ofSeconds(TTL_SECONDS - 1));

        // When
        engine.refreshIfExpiredOnRead();

        // Then
        assertTrue(transport.requestUrls.isEmpty());
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Refreshes on read once the TTL expired")
    void refreshesOnReadOnceTtlExpired() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));
        clock.advance(Duration.ofSeconds(TTL_SECONDS));

        // When
        engine.refreshIfExpiredOnRead();
        engine.refreshIfExpiredOnRead();

        // Then
        assertEquals(1, transport.requestUrls.size());
        assertTrue(engine.hasFeatureData());
        assertEquals(clock.instant().getEpochSecond() + TTL_SECONDS, engine.expiresAt().get());
        assertEquals(FeatureRefreshSource.POLLING, listener.events.get(0).getSource());
    }

    @Test
    @DisplayName("Keeps held features when a refresh on read fails")
    void keepsHeldFeaturesWhenRefreshOnReadFails() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
        FeatureSnapshot before = engine.snapshot();
        transport.fail(new IOException("connection refused"));
        clock.advance(Duration.ofSeconds(TTL_SECONDS));

        // When
        engine.refreshIfExpiredOnRead();

        // Then
        assertSame(before, engine.snapshot());
        assertEquals(1, engine.metrics().failureCount());
        assertEquals(clock.instant().getEpochSecond() + TTL_SECONDS, engine.expiresAt().get());
    }

    @Test
    @DisplayName("Does not throw when a refresh on read fails without features")
    void doesNotThrowWhenRefreshOnReadFailsWithoutFeatures() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.fail(new IOException("connection refused"));
        clock.advance(Duration.ofSeconds(TTL_SECONDS));

        // When
        engine.refreshIfExpiredOnRead();

        // Then
        assertFalse(engine.hasFeatureData());
        assertInstanceOf(FeatureFetchException.class, engine.metrics().lastError());
    }

    @Test
    @DisplayName("Does not refresh on read under a strategy other than stale-while-revalidate")
    void doesNotRefreshOnReadUnderOtherStrategy() {
        // Given
        FeatureRefreshEngine engine = wire(builder().strategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS).build());
        transport.respond(ok(BODY, null));
        clock.advance(Duration.ofSeconds(TTL_SECONDS));

        // When
        engine.refreshIfExpiredOnRead();

        // Then
        assertTrue(transport.requestUrls.isEmpty());
    }

    @Test
    @DisplayName("Does not refresh on read when the engine refreshes by polling")
    void doesNotRefreshOnReadWhenPolling() {
        // Given
        FeatureRefreshEngine engine = wire(builder().swrRefresh(FeatureRefreshEngine.SwrRefresh.POLLING).build());
        transport.respond(ok(BODY, null));
        clock.advance(Duration.ofSeconds(TTL_SECONDS));

        // When
        engine.refreshIfExpiredOnRead();

        // Then
        assertTrue(transport.requestUrls.isEmpty());
    }

    private FeatureRefreshEngine engine() {
        return wire(builder().build());
    }

    private FeatureRefreshEngine wire(FeatureRefreshEngine engine) {
        engine.addListener(listener);
        return engine;
    }

    private FeatureRefreshEngine.FeatureRefreshEngineBuilder builder() {
        return FeatureRefreshEngine.builder()
                .endpoints(ENDPOINTS)
                .transport(transport)
                .cache(new FeatureCacheStore(ENDPOINTS.cacheKey(), false, cacheManager))
                .swrTtlSeconds(() -> TTL_SECONDS)
                .retryPolicy(new FeatureFetchRetryPolicy(3, Duration.ZERO, Duration.ZERO))
                .swrRefresh(FeatureRefreshEngine.SwrRefresh.ON_READ)
                .clock(clock);
    }

    private static TransportResponse ok(@Nullable String body, @Nullable String eTag) {
        Map<String, String> headers = new HashMap<>();
        if (eTag != null) {
            headers.put("ETag", eTag);
        }
        return TransportResponse.of(200, "OK", body, headers);
    }

    private static final class FakeTransport implements FeatureTransport {
        private final Deque<Object> script = new ArrayDeque<>();
        @Nullable
        private Object current;
        private final List<String> requestUrls = new ArrayList<>();
        private final List<Map<String, String>> requestHeaders = new ArrayList<>();

        void respond(TransportResponse response) {
            script.add(response);
        }

        void fail(IOException error) {
            script.add(error);
        }

        @Override
        public TransportResponse get(String url, Map<String, String> headers) throws IOException {
            requestUrls.add(url);
            requestHeaders.add(new HashMap<>(headers));
            if (!script.isEmpty()) {
                current = script.poll();
            }
            if (current instanceof IOException) {
                throw (IOException) current;
            }
            return (TransportResponse) current;
        }

        @Override
        public TransportResponse postJson(String url, String json) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SseConnection openEvents(String url, SseListener listener) {
            return () -> { };
        }

        @Override
        public void closeEvents() {
        }
    }



}
