package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureRefreshEngineLifecycleTest {

    private static final int TTL_SECONDS = 60;
    private static final FeatureEndpoints ENDPOINTS = FeatureEndpoints.of("http://localhost", "sdk-test");
    private static final String FEATURES_JSON = "{\"flag-a\":{\"defaultValue\":true}}";
    private static final String OTHER_FEATURES_JSON = "{\"flag-b\":{\"defaultValue\":false}}";
    private static final String BODY = "{\"features\":" + FEATURES_JSON + "}";
    private static final String OTHER_BODY = "{\"features\":" + OTHER_FEATURES_JSON + "}";

    private final FakeTransport transport = new FakeTransport();
    private final InMemoryCacheManager cacheManager = new InMemoryCacheManager();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final RecordingRefreshCallback callback = new RecordingRefreshCallback();
    private final RecordingRefreshListener listener = new RecordingRefreshListener();

    @Test
    @DisplayName("Loads features once when stale-while-revalidate initialize is called twice")
    void loadsFeaturesOnceWhenInitializeCalledTwice() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));

        // When
        engine.initialize(false);
        engine.initialize(false);

        // Then
        assertEquals(1, transport.getUrls.size());
        assertTrue(engine.initialized().get());
        assertTrue(engine.hasFeatureData());
        assertEquals(FeatureRefreshSource.INITIALIZATION, listener.events.get(0).getSource());
    }

    @Test
    @DisplayName("Stays uninitialized when initialize fails without network or cache")
    void staysUninitializedWhenInitializeFails() {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        transport.fail(new IOException("connection refused"));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class, () -> engine.initialize(false));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, error.getErrorCode());
        assertFalse(engine.initialized().get());
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Initializes on a later call after a failed attempt")
    void initializesOnLaterCallAfterFailedAttempt() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        transport.fail(new IOException("connection refused"));
        assertThrows(FeatureFetchException.class, () -> engine.initialize(false));
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));

        // When
        engine.initialize(false);

        // Then
        assertTrue(engine.initialized().get());
        assertEquals(FEATURES_JSON, engine.snapshot().getFeaturesJson());
    }

    @Test
    @DisplayName("Initializes from the cache when the network fails")
    void initializesFromCacheWhenNetworkFails() throws Exception {
        // Given
        cacheManager.store.put(ENDPOINTS.cacheKey(), BODY);
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        transport.fail(new IOException("connection refused"));

        // When
        engine.initialize(false);

        // Then
        assertTrue(engine.initialized().get());
        assertEquals(FEATURES_JSON, engine.snapshot().getFeaturesJson());
        assertEquals(Boolean.TRUE, engine.metrics().lastLoadedFromCache());
    }

    @Test
    @DisplayName("Falls back to stale-while-revalidate without opening the stream when the server does not announce SSE support")
    void fallsBackToStaleWhileRevalidateWithoutSseSupport() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));

        // When
        engine.initialize(false);

        // Then
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, engine.strategy());
        assertFalse(engine.isSseAllowed());
        assertTrue(transport.eventUrls.isEmpty());
        engine.shutdown();
    }

    @Test
    @DisplayName("Keeps the SSE strategy and opens the events stream when the server supports SSE")
    void keepsSseStrategyAndOpensEventsWhenServerSupportsSse() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        transport.respond(ok(BODY, sseEnabled()));

        // When
        engine.initialize(false);

        // Then
        assertEquals(FeatureRefreshStrategy.SERVER_SENT_EVENTS, engine.strategy());
        assertTrue(engine.isSseAllowed());
        assertEquals(Collections.singletonList(ENDPOINTS.events()), transport.eventUrls);
        engine.shutdown();
    }

    @Test
    @DisplayName("Posts the configured body to the remote evaluation endpoint on initialize")
    void postsConfiguredBodyToRemoteEvalEndpointOnInitialize() throws Exception {
        // Given
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "user-1");
        RequestBodyForRemoteEval body = new RequestBodyForRemoteEval(attributes, null, null, null);
        FeatureRefreshEngine engine = wire(builder()
                .strategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .remoteEvalBody(body)
                .build());
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));

        // When
        engine.initialize(false);

        // Then
        assertEquals(Collections.singletonList(ENDPOINTS.remoteEval()), transport.postUrls);
        assertEquals(GrowthBookJsonUtils.getInstance().gson.toJson(body), transport.postBodies.get(0));
        assertTrue(transport.getUrls.isEmpty());
        assertTrue(engine.initialized().get());
        assertEquals(FeatureRefreshSource.REMOTE_EVALUATION, listener.events.get(0).getSource());
    }

    @Test
    @DisplayName("Applies a successful remote evaluation response as the features payload")
    void appliesSuccessfulRemoteEvalResponse() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY);
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));

        // When
        engine.fetchForRemoteEval(null, FeatureRefreshSource.REMOTE_EVALUATION);

        // Then
        assertEquals(FEATURES_JSON, engine.snapshot().getFeaturesJson());
        assertEquals(GrowthBookJsonUtils.getInstance().gson.toJson(new RequestBodyForRemoteEval()), transport.postBodies.get(0));
        assertTrue(listener.events.get(0).isSuccessful());
        assertEquals(1, engine.metrics().successCount());
    }

    @Test
    @DisplayName("Reports an error status from remote evaluation without throwing")
    void reportsRemoteEvalErrorStatusWithoutThrowing() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY);
        transport.respond(TransportResponse.of(500, "Internal Server Error", "", Collections.<String, String>emptyMap()));

        // When
        engine.fetchForRemoteEval(null, FeatureRefreshSource.REMOTE_EVALUATION);

        // Then
        assertEquals("Response is not success, response code is:500. And message is: Internal Server Error",
                callback.errors.get(0).getMessage());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertEquals(FeatureRefreshSource.REMOTE_EVALUATION, event.getSource());
        assertEquals(1, engine.metrics().failureCount());
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Throws a no-response error and notifies callbacks when remote evaluation gets no response")
    void throwsNoResponseErrorWhenRemoteEvalGetsNoResponse() {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY);
        transport.fail(new IOException("connection refused"));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.fetchForRemoteEval(null, FeatureRefreshSource.REMOTE_EVALUATION));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, error.getErrorCode());
        assertSame(error, callback.errors.get(0));
        assertFalse(listener.events.get(0).isSuccessful());
        assertEquals(1, engine.metrics().failureCount());
    }

    @Test
    @DisplayName("Publishes a feature payload received over SSE")
    void publishesFeaturePayloadReceivedOverSse() throws Exception {
        // Given
        FeatureRefreshEngine engine = initializedSseEngine();

        // When
        transport.sseListener.onEvent("features", OTHER_BODY);

        // Then
        assertEquals(OTHER_FEATURES_JSON, engine.snapshot().getFeaturesJson());
        FeatureRefreshEvent event = lastEvent();
        assertTrue(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.SSE, event.getSource());
        assertEquals(OTHER_FEATURES_JSON, lastRefresh());
        engine.shutdown();
    }

    @Test
    @DisplayName("Notifies callbacks and listeners without changing features on an SSE event without payload")
    void notifiesWithoutChangingFeaturesOnSseEventWithoutPayload() throws Exception {
        // Given
        FeatureRefreshEngine engine = initializedSseEngine();
        FeatureSnapshot before = engine.snapshot();
        int eventsBefore = listener.events.size();

        // When
        transport.sseListener.onEvent("features", "");

        // Then
        assertSame(before, engine.snapshot());
        assertEquals(eventsBefore + 1, listener.events.size());
        FeatureRefreshEvent event = lastEvent();
        assertTrue(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.SSE, event.getSource());
        assertEquals(FEATURES_JSON, lastRefresh());
        engine.shutdown();
    }

    @Test
    @DisplayName("Ignores SSE heartbeats")
    void ignoresSseHeartbeats() throws Exception {
        // Given
        FeatureRefreshEngine engine = initializedSseEngine();
        int eventsBefore = listener.events.size();
        int refreshesBefore = callback.refreshes.size();

        // When
        transport.sseListener.onEvent("heartbeat", OTHER_BODY);

        // Then
        assertEquals(FEATURES_JSON, engine.snapshot().getFeaturesJson());
        assertEquals(eventsBefore, listener.events.size());
        assertEquals(refreshesBefore, callback.refreshes.size());
        engine.shutdown();
    }

    @Test
    @DisplayName("Reports a failure and keeps features on an unusable SSE payload")
    void reportsFailureAndKeepsFeaturesOnUnusableSsePayload() throws Exception {
        // Given
        FeatureRefreshEngine engine = initializedSseEngine();
        FeatureSnapshot before = engine.snapshot();

        // When
        transport.sseListener.onEvent("features", "{\"features\":null}");

        // Then
        assertSame(before, engine.snapshot());
        FeatureRefreshEvent event = lastEvent();
        assertFalse(event.isSuccessful());
        assertEquals(FeatureRefreshSource.SSE, event.getSource());
        assertEquals(1, engine.metrics().failureCount());
        engine.shutdown();
    }

    @Test
    @DisplayName("Reports SSE connected once the stream opens")
    void reportsSseConnectedOnceStreamOpens() throws Exception {
        // Given
        FeatureRefreshEngine engine = initializedSseEngine();

        // When
        transport.sseListener.onOpen();

        // Then
        assertTrue(engine.isSseConnected());
        engine.shutdown();
    }

    @Test
    @DisplayName("Stops notifying callbacks and listeners after shutdown")
    void stopsNotifyingCallbacksAndListenersAfterShutdown() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));

        // When
        engine.shutdown();
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        assertTrue(callback.refreshes.isEmpty());
        assertTrue(listener.events.isEmpty());
    }

    @Test
    @DisplayName("Releases the cache manager on shutdown without clearing the cache and keeps features readable")
    void releasesCacheManagerOnShutdownWithoutClearingCache() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        transport.respond(ok(BODY, Collections.<String, String>emptyMap()));
        engine.initialize(false);

        // When
        engine.shutdown();

        // Then
        assertNull(engine.cache().manager());
        assertEquals(BODY, cacheManager.store.get(ENDPOINTS.cacheKey()));
        assertFalse(cacheManager.cleared());
        assertEquals(FEATURES_JSON, engine.snapshot().getFeaturesJson());
        assertTrue(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Reports SSE disconnected and closes the stream after shutdown")
    void reportsSseDisconnectedAndClosesStreamAfterShutdown() throws Exception {
        // Given
        FeatureRefreshEngine engine = initializedSseEngine();
        transport.sseListener.onOpen();

        // When
        engine.shutdown();

        // Then
        assertFalse(engine.isSseConnected());
        assertEquals(1, transport.cancelCount);
        assertTrue(transport.eventsClosed);
    }

    private FeatureRefreshEngine initializedSseEngine() throws FeatureFetchException {
        FeatureRefreshEngine engine = engine(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        transport.respond(ok(BODY, sseEnabled()));
        engine.initialize(false);
        return engine;
    }

    private FeatureRefreshEvent lastEvent() {
        return listener.events.get(listener.events.size() - 1);
    }

    private String lastRefresh() {
        return callback.refreshes.get(callback.refreshes.size() - 1);
    }

    private FeatureRefreshEngine engine(FeatureRefreshStrategy strategy) {
        return wire(builder().strategy(strategy).build());
    }

    private FeatureRefreshEngine wire(FeatureRefreshEngine engine) {
        engine.addCallback(callback);
        engine.addListener(listener);
        return engine;
    }

    private FeatureRefreshEngine.FeatureRefreshEngineBuilder builder() {
        return FeatureRefreshEngine.builder()
                .endpoints(ENDPOINTS)
                .transport(transport)
                .cache(new FeatureCacheStore(ENDPOINTS.cacheKey(), false, cacheManager))
                .swrTtlSeconds(TTL_SECONDS)
                .retryPolicy(new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO))
                .swrRefresh(FeatureRefreshEngine.SwrRefresh.ON_READ)
                .clock(clock);
    }

    private static Map<String, String> sseEnabled() {
        return Collections.singletonMap("X-Sse-Support", "enabled");
    }

    private static TransportResponse ok(String body, Map<String, String> headers) {
        return TransportResponse.of(200, "OK", body, headers);
    }

    private static final class FakeTransport implements FeatureTransport {
        private final Deque<Object> script = new ArrayDeque<>();
        @Nullable
        private Object current;
        private final List<String> getUrls = new ArrayList<>();
        private final List<String> postUrls = new ArrayList<>();
        private final List<String> postBodies = new ArrayList<>();
        private final List<String> eventUrls = new ArrayList<>();
        @Nullable
        private SseListener sseListener;
        private int cancelCount;
        private boolean eventsClosed;

        void respond(TransportResponse response) {
            script.add(response);
        }

        void fail(IOException error) {
            script.add(error);
        }

        @Override
        public TransportResponse get(String url, Map<String, String> headers) throws IOException {
            getUrls.add(url);
            return next();
        }

        @Override
        public TransportResponse postJson(String url, String json) throws IOException {
            postUrls.add(url);
            postBodies.add(json);
            return next();
        }

        @Override
        public SseConnection openEvents(String url, SseListener listener) {
            eventUrls.add(url);
            sseListener = listener;
            return () -> cancelCount++;
        }

        @Override
        public void closeEvents() {
            eventsClosed = true;
        }

        private TransportResponse next() throws IOException {
            if (!script.isEmpty()) {
                current = script.poll();
            }
            if (current instanceof IOException) {
                throw (IOException) current;
            }
            return (TransportResponse) current;
        }
    }



}
