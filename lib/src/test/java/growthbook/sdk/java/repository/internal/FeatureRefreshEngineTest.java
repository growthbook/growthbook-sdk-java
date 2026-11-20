package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureRefreshEngineTest {

    private static final int TTL_SECONDS = 60;
    private static final FeatureEndpoints ENDPOINTS = FeatureEndpoints.of("http://localhost", "sdk-test");
    private static final String FEATURES_JSON = "{\"flag-a\":{\"defaultValue\":true}}";
    private static final String OTHER_FEATURES_JSON = "{\"flag-b\":{\"defaultValue\":false}}";
    private static final String SAVED_GROUPS_JSON = "{\"group-1\":[\"user-1\"]}";
    private static final String BODY = "{\"features\":" + FEATURES_JSON + ",\"savedGroups\":" + SAVED_GROUPS_JSON + "}";
    private static final String OTHER_BODY = "{\"features\":" + OTHER_FEATURES_JSON + "}";
    private static final String DECRYPTION_KEY = "Ns04T5n9+59rl2x3SlNHtQ==";

    private final FakeTransport transport = new FakeTransport();
    private final FakeCacheManager cacheManager = new FakeCacheManager();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RecordingCallback callback = new RecordingCallback();
    private final RecordingListener listener = new RecordingListener();

    @Test
    @DisplayName("Publishes the fetched features and saved groups as one snapshot")
    void publishesFetchedFeaturesAndSavedGroupsAsOneSnapshot() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, "\"v1\""));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        FeatureSnapshot snapshot = engine.snapshot();
        assertEquals(FEATURES_JSON, snapshot.getFeaturesJson());
        assertEquals(SAVED_GROUPS_JSON, snapshot.getSavedGroupsJson());
        assertTrue(snapshot.getParsedFeatures().containsKey("flag-a"));
        assertTrue(snapshot.getParsedSavedGroups().has("group-1"));
        assertTrue(engine.hasFeatureData());
        assertEquals(1, engine.activeFeatureCount());
    }

    @Test
    @DisplayName("Records metrics and notifies callbacks and listeners after a successful fetch")
    void recordsMetricsAndNotifiesAfterSuccessfulFetch() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals(1, engine.metrics().successCount());
        assertEquals(clock.millis(), engine.metrics().lastSuccessfulFetchAtMillis());
        assertEquals(Boolean.FALSE, engine.metrics().lastLoadedFromCache());
        assertEquals(Collections.singletonList(FEATURES_JSON), callback.refreshes);
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertTrue(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
        assertEquals(1, event.getActiveFeatureCount());
    }

    @Test
    @DisplayName("Saves the raw response body to the cache")
    void savesRawResponseBodyToCache() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals(BODY, cacheManager.store.get(ENDPOINTS.cacheKey()));
    }

    @Test
    @DisplayName("Reports features unchanged when the same payload is fetched again")
    void reportsFeaturesUnchangedWhenSamePayloadFetchedAgain() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // When
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals(2, listener.events.size());
        assertFalse(listener.events.get(1).isFeaturesChanged());
    }

    @Test
    @DisplayName("Keeps the snapshot and refreshes the expiry on 304 Not Modified")
    void keepsSnapshotAndRefreshesExpiryOnNotModified() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, "\"v1\""));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
        FeatureSnapshot before = engine.snapshot();
        clock.advance(Duration.ofSeconds(30));
        transport.respond(status(304, "Not Modified"));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertSame(before, engine.snapshot());
        assertEquals(clock.instant().getEpochSecond() + TTL_SECONDS, engine.expiresAt().get());
        assertEquals(2, engine.metrics().successCount());
        assertEquals(0, engine.metrics().failureCount());
        FeatureRefreshEvent event = listener.events.get(1);
        assertTrue(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
    }

    @Test
    @DisplayName("Sends max-age without If-None-Match while no ETag is remembered")
    void sendsMaxAgeWithoutIfNoneMatchWhileNoETagRemembered() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        Map<String, String> headers = transport.requestHeaders.get(0);
        assertEquals("max-age=" + TTL_SECONDS, headers.get("Cache-Control"));
        assertFalse(headers.containsKey("If-None-Match"));
    }

    @Test
    @DisplayName("Uses the TTL in effect at each refresh for max-age and expiry")
    void usesTtlInEffectAtEachRefresh() throws Exception {
        // Given
        AtomicInteger ttlSeconds = new AtomicInteger(TTL_SECONDS);
        FeatureRefreshEngine engine = wire(builder().swrTtlSeconds(ttlSeconds::get).build());
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // When
        ttlSeconds.set(5);
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals("max-age=5", transport.requestHeaders.get(1).get("Cache-Control"));
        assertEquals(clock.instant().getEpochSecond() + 5, engine.expiresAt().get());
    }

    @Test
    @DisplayName("Sends the remembered ETag and max-age on the next default refresh")
    void sendsRememberedETagAndMaxAgeOnNextDefaultRefresh() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, "\"v1\""));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        Map<String, String> headers = transport.requestHeaders.get(1);
        assertEquals("\"v1\"", headers.get("If-None-Match"));
        assertEquals("max-age=" + TTL_SECONDS, headers.get("Cache-Control"));
    }

    @Test
    @DisplayName("Sends no-cache and no ETag on a forced refresh")
    void sendsNoCacheAndNoETagOnForcedRefresh() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, "\"v1\""));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // When
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        Map<String, String> headers = transport.requestHeaders.get(1);
        assertEquals("no-cache", headers.get("Cache-Control"));
        assertFalse(headers.containsKey("If-None-Match"));
    }

    @Test
    @DisplayName("Sends no conditional headers to an endpoint outside the features pattern")
    void sendsNoConditionalHeadersOutsideFeaturesPattern() throws Exception {
        // Given
        FeatureEndpoints custom = FeatureEndpoints.of("http://localhost", "sdk-test/v2");
        FeatureRefreshEngine engine = wire(builder().endpoints(custom).build());
        transport.respond(ok(BODY, "\"v1\""));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        assertTrue(transport.requestHeaders.get(0).isEmpty());
        assertTrue(transport.requestHeaders.get(1).isEmpty());
        assertTrue(transport.requestHeaders.get(2).isEmpty());
    }

    @Test
    @DisplayName("Remembers the ETag only after the payload applied")
    void remembersETagOnlyAfterPayloadApplied() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok("{\"features\":null}", "\"v1\""));
        assertThrows(FeatureFetchException.class, () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));
        transport.respond(ok(BODY, "\"v2\""));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertFalse(transport.requestHeaders.get(1).containsKey("If-None-Match"));
        assertTrue(engine.hasFeatureData());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "{\"features\":null}", "{\"features\":\"flag-a\"}"})
    @DisplayName("Rejects an unusable body with a configuration error and keeps the cache untouched")
    void rejectsUnusableBodyWithConfigurationError(String body) {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(body, null));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR, error.getErrorCode());
        assertEquals(1, transport.requestUrls.size());
        assertEquals(0, cacheManager.saveCount);
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Rejects a plain body when a decryption key is configured")
    void rejectsPlainBodyWhenDecryptionKeyConfigured() {
        // Given
        FeatureRefreshEngine engine = wire(builder().decryptionKey(DECRYPTION_KEY).build());
        transport.respond(ok(BODY, null));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR, error.getErrorCode());
        assertEquals(0, cacheManager.saveCount);
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Keeps held features and the cached payload when a later body is unusable")
    void keepsHeldFeaturesAndCachedPayloadWhenLaterBodyUnusable() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
        FeatureSnapshot before = engine.snapshot();
        transport.respond(ok("{\"features\":null}", null));

        // When
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        assertSame(before, engine.snapshot());
        assertEquals(BODY, cacheManager.store.get(ENDPOINTS.cacheKey()));
        assertEquals(1, cacheManager.saveCount);
        assertEquals(1, engine.metrics().failureCount());
        FeatureFetchException lastError = assertInstanceOf(FeatureFetchException.class, engine.metrics().lastError());
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR, lastError.getErrorCode());
    }

    @Test
    @DisplayName("Retries a retryable status up to the maximum attempts and then throws")
    void retriesRetryableStatusUpToMaxAttemptsThenThrows() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(status(503, "Service Unavailable"));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR, error.getErrorCode());
        assertEquals(3, transport.requestUrls.size());
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Does not retry a non-retryable status")
    void doesNotRetryNonRetryableStatus() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(status(404, "Not Found"));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR, error.getErrorCode());
        assertEquals(1, transport.requestUrls.size());
    }

    @Test
    @DisplayName("Retries a response without a body and then throws a no-response error")
    void retriesResponseWithoutBodyThenThrowsNoResponseError() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(null, null));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, error.getErrorCode());
        assertEquals(3, transport.requestUrls.size());
    }

    @Test
    @DisplayName("Throws a no-response error and reports the cause when every attempt fails without a cache")
    void throwsNoResponseErrorWhenEveryAttemptFailsWithoutCache() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.fail(new IOException("connection refused"));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, error.getErrorCode());
        assertEquals(3, transport.requestUrls.size());
        assertEquals(1, callback.errors.size());
        assertInstanceOf(IOException.class, callback.errors.get(0));
        assertSame(error, engine.metrics().lastError());
    }

    @Test
    @DisplayName("Succeeds when a retry after an IOException gets a response")
    void succeedsWhenRetryAfterIOExceptionGetsResponse() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.fail(new IOException("connection reset"));
        transport.respond(ok(BODY, null));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals(2, transport.requestUrls.size());
        assertTrue(engine.hasFeatureData());
        assertEquals(0, engine.metrics().failureCount());
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    @DisplayName("Loads cached features when every attempt fails and no features are held")
    void loadsCachedFeaturesWhenEveryAttemptFailsAndNoFeaturesHeld() throws Exception {
        // Given
        cacheManager.store.put(ENDPOINTS.cacheKey(), BODY);
        FeatureRefreshEngine engine = engine();
        transport.respond(status(503, "Service Unavailable"));

        // When
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertTrue(engine.hasFeatureData());
        assertEquals(FEATURES_JSON, engine.snapshot().getFeaturesJson());
        assertEquals(Boolean.TRUE, engine.metrics().lastLoadedFromCache());
        assertEquals(1, engine.metrics().failureCount());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertTrue(event.isLoadedFromCache());
        assertTrue(event.isFeaturesChanged());
    }

    @Test
    @DisplayName("Throws when every attempt fails and the cached payload is unusable")
    void throwsWhenEveryAttemptFailsAndCachedPayloadUnusable() {
        // Given
        cacheManager.store.put(ENDPOINTS.cacheKey(), "{\"features\":null}");
        FeatureRefreshEngine engine = engine();
        transport.fail(new IOException("connection refused"));

        // When
        FeatureFetchException error = assertThrows(FeatureFetchException.class,
                () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL));

        // Then
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, error.getErrorCode());
        assertFalse(engine.hasFeatureData());
    }

    @Test
    @DisplayName("Keeps held features without throwing when a later refresh fails")
    void keepsHeldFeaturesWhenLaterRefreshFails() throws Exception {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
        FeatureSnapshot before = engine.snapshot();
        transport.fail(new IOException("connection refused"));

        // When
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        assertSame(before, engine.snapshot());
        assertEquals(1, engine.metrics().failureCount());
        assertEquals(1, engine.metrics().consecutiveFailures());
        assertEquals(Boolean.FALSE, engine.metrics().lastLoadedFromCache());
        assertFalse(listener.events.get(1).isLoadedFromCache());
    }

    @Test
    @DisplayName("Fetches on a forced refresh even within the background fetch interval")
    void fetchesOnForcedRefreshWithinBackgroundFetchInterval() throws Exception {
        // Given
        FeatureRefreshEngine engine = wire(builder().backgroundFetchInterval(Duration.ofHours(1)).build());
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);
        transport.respond(ok(OTHER_BODY, null));

        // When
        engine.refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals(2, transport.requestUrls.size());
        assertEquals(OTHER_FEATURES_JSON, engine.snapshot().getFeaturesJson());
    }

    @Test
    @DisplayName("Skips a default refresh within the background fetch interval, measured on the injected clock")
    void skipsDefaultRefreshWithinBackgroundFetchInterval() throws Exception {
        // Given
        FeatureRefreshEngine engine = wire(builder().backgroundFetchInterval(Duration.ofHours(1)).build());
        transport.respond(ok(BODY, null));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // When
        clock.advance(Duration.ofMinutes(59));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);
        int fetchesWithinInterval = transport.requestUrls.size();
        clock.advance(Duration.ofMinutes(2));
        engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.MANUAL);

        // Then
        assertEquals(1, fetchesWithinInterval);
        assertEquals(2, transport.requestUrls.size());
    }

    @Test
    @DisplayName("Records the failure and notifies listeners even when a refresh fails without any features")
    void recordsFailureAndNotifiesListenersWhenRefreshThrows() {
        // Given
        FeatureRefreshEngine engine = engine();
        transport.fail(new IOException("connection refused"));

        // When
        assertThrows(FeatureFetchException.class, () -> engine.refresh(RefreshMode.DEFAULT, FeatureRefreshSource.INITIALIZATION));

        // Then
        assertEquals(1, engine.metrics().failureCount());
        assertEquals(1, listener.events.size());
        assertFalse(listener.events.get(0).isSuccessful());
        assertEquals(FeatureRefreshSource.INITIALIZATION, listener.events.get(0).getSource());
    }

    private FeatureRefreshEngine engine() {
        return wire(builder().build());
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

    private static TransportResponse status(int code, String reason) {
        return TransportResponse.of(code, reason, "", Collections.<String, String>emptyMap());
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

    private static final class FakeCacheManager implements GbCacheManager {
        private final Map<String, String> store = new HashMap<>();
        private int saveCount;

        @Override
        public void saveContent(String key, String data) {
            saveCount++;
            store.put(key, data);
        }

        @Override
        public String loadCache(String key) {
            return store.get(key);
        }

        @Override
        public void clearCache() {
            store.clear();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class RecordingCallback implements FeatureRefreshCallback {
        private final List<String> refreshes = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onRefresh(String featuresJson) {
            refreshes.add(featuresJson);
        }

        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable);
        }
    }

    private static final class RecordingListener implements FeatureRefreshListener {
        private final List<FeatureRefreshEvent> events = new ArrayList<>();

        @Override
        public void onRefresh(FeatureRefreshEvent event) {
            events.add(event);
        }
    }
}
