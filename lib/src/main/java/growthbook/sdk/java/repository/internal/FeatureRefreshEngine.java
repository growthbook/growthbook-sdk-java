package growthbook.sdk.java.repository.internal;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.RetryableFeatureFetchException;
import growthbook.sdk.java.featurefetch.FeatureFetchFailureHandler;
import growthbook.sdk.java.featurefetch.FeatureFetchHttpStatus;
import growthbook.sdk.java.featurefetch.FeatureRefreshCacheFreshness;
import growthbook.sdk.java.featurefetch.FeatureRefreshScheduler;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.LruETagCache;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.retry.FeatureFetchRetryExecutor;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

/**
 * The refresh core both feature repositories share: fetching with retries, conditional requests
 * and ETags, falling back to the cache, parsing and decrypting the payload, publishing it as one
 * atomic {@link FeatureSnapshot}, server-sent events, remote evaluation, metrics and
 * notifications. The repositories differ only in their {@link FeatureTransport} and in how a
 * stale-while-revalidate refresh is triggered ({@link SwrRefresh}).
 *
 * <p>Shared across threads: readers never block on a refresh, and every refresh path — caller,
 * poller, SSE and retry threads — publishes through the same atomic snapshot.
 */
@Slf4j
public final class FeatureRefreshEngine {

    /**
     * How a stale-while-revalidate repository keeps its features fresh.
     */
    public enum SwrRefresh {
        /**
         * A background thread refreshes every TTL.
         */
        POLLING,
        /**
         * A read after the TTL expired refreshes on the reading thread; no background thread.
         */
        ON_READ
    }

    private static final String SSE_ENABLED = "enabled";
    private static final String ETAG = "ETag";

    private final FeatureEndpoints endpoints;
    private final FeatureTransport transport;
    private final FeatureCacheStore cache;
    private final PayloadParser parser;
    private final RefreshMetrics metrics;
    private final RefreshCallbacks callbacks = new RefreshCallbacks();
    private final FeatureRefreshNotifier notifier;
    private final FeatureFetchRetryExecutor retryExecutor;
    private final FeatureRefreshScheduler refreshScheduler = new FeatureRefreshScheduler();
    private final LruETagCache eTags = new LruETagCache(100);
    private final RefreshPoller poller;
    private final SseSession sse;
    private final Clock clock;
    private final IntSupplier swrTtlSeconds;
    @Nullable
    private final Duration backgroundFetchInterval;
    private final SwrRefresh swrRefresh;
    @Nullable
    private final RequestBodyForRemoteEval remoteEvalBody;
    private volatile FeatureRefreshStrategy strategy;
    private final AtomicReference<FeatureSnapshot> snapshot = new AtomicReference<>(FeatureSnapshot.EMPTY);
    private final AtomicBoolean hasFeatureData = new AtomicBoolean();
    private final AtomicLong expiresAt = new AtomicLong();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean sseAllowed = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final Object initLock = new Object();
    private final Object readRefreshLock = new Object();

    /**
     * @param endpoints               the client's endpoints
     * @param transport               the HTTP implementation
     * @param cache                   the persisted payload
     * @param decryptionKey           the key for encrypted payloads, or {@code null}
     * @param strategy                how features are refreshed, stale-while-revalidate when {@code null}
     * @param swrTtlSeconds           the stale-while-revalidate TTL, read on each use
     * @param backgroundFetchInterval the minimum interval between non-forced refreshes, or {@code null}
     * @param retryPolicy             the retry policy for fetches and SSE reconnects
     * @param swrRefresh              how a stale-while-revalidate refresh is triggered
     * @param remoteEvalBody          the request body remote evaluation initializes with
     * @param clock                   the time source
     */
    @Builder
    private FeatureRefreshEngine(
            FeatureEndpoints endpoints,
            FeatureTransport transport,
            FeatureCacheStore cache,
            @Nullable String decryptionKey,
            @Nullable FeatureRefreshStrategy strategy,
            IntSupplier swrTtlSeconds,
            @Nullable Duration backgroundFetchInterval,
            FeatureFetchRetryPolicy retryPolicy,
            SwrRefresh swrRefresh,
            @Nullable RequestBodyForRemoteEval remoteEvalBody,
            @Nullable Clock clock
    ) {
        this.endpoints = endpoints;
        this.transport = transport;
        this.cache = cache;
        this.parser = new PayloadParser(decryptionKey);
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.metrics = new RefreshMetrics(this.clock);
        this.strategy = strategy != null ? strategy : FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        this.swrTtlSeconds = swrTtlSeconds;
        this.backgroundFetchInterval = backgroundFetchInterval;
        this.swrRefresh = swrRefresh;
        this.remoteEvalBody = remoteEvalBody;
        this.retryExecutor = new FeatureFetchRetryExecutor(retryPolicy);
        this.notifier = new FeatureRefreshNotifier(this::activeFeatureCount, () -> this.strategy);
        this.poller = new RefreshPoller(() -> refresh(RefreshMode.DEFAULT, FeatureRefreshSource.POLLING), this.clock);
        this.sse = new SseSession(transport, endpoints.events(), retryPolicy, new SseHandler());
        refreshExpiresAt();
    }

    /**
     * Loads the first features and starts the strategy's refreshing. Idempotent; on failure the
     * engine stays uninitialized so a later call can retry.
     *
     * @param retryOnFailure whether a server-sent events connection reconnects when it ends
     * @throws FeatureFetchException when there are no features from the network or the cache
     */
    public void initialize(boolean retryOnFailure) throws FeatureFetchException {
        if (initialized.get()) {
            return;
        }
        synchronized (initLock) {
            if (initialized.get()) {
                return;
            }
            switch (strategy) {
                case STALE_WHILE_REVALIDATE:
                    refresh(RefreshMode.DEFAULT, FeatureRefreshSource.INITIALIZATION);
                    if (swrRefresh == SwrRefresh.POLLING) {
                        poller.start(swrTtlSeconds.getAsInt());
                    }
                    break;
                case SERVER_SENT_EVENTS:
                    refresh(RefreshMode.DEFAULT, FeatureRefreshSource.INITIALIZATION);
                    startSse(retryOnFailure);
                    break;
                case REMOTE_EVAL_STRATEGY:
                    fetchForRemoteEval(remoteEvalBody, FeatureRefreshSource.REMOTE_EVALUATION);
                    break;
            }
            initialized.set(true);
        }
    }

    /**
     * Refreshes now, unless a recent fetch or cache write makes it unnecessary.
     *
     * @throws FeatureFetchException when every attempt failed and there are no features to keep
     */
    public void refresh(@Nullable RefreshMode refreshMode, FeatureRefreshSource source) throws FeatureFetchException {
        RefreshMode mode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        if (shouldSkipRefresh(mode)) {
            log.debug("Skipping feature refresh because cached features are newer than the background fetch interval.");
            return;
        }
        long startedAtNanos = System.nanoTime();
        Optional<FeatureFetchException> failure = retryExecutor.execute(() -> fetchOnce(mode, source, startedAtNanos));
        if (failure.isPresent()) {
            handleFetchFailure(failure.get(), source, startedAtNanos);
        }
    }

    /**
     * Schedules a refresh without waiting for it.
     */
    public void requestRefresh(@Nullable RefreshMode refreshMode) {
        refreshScheduler.requestRefresh(refreshMode, mode -> refresh(mode, FeatureRefreshSource.MANUAL));
    }

    /**
     * For {@link SwrRefresh#ON_READ}: refreshes on the calling thread when the TTL has expired.
     * Concurrent readers wait for that one refresh; a failure is logged and the held features
     * stay.
     */
    public void refreshIfExpiredOnRead() {
        if (swrRefresh != SwrRefresh.ON_READ || strategy != FeatureRefreshStrategy.STALE_WHILE_REVALIDATE || !isExpired()) {
            return;
        }
        synchronized (readRefreshLock) {
            if (!isExpired()) {
                return;
            }
            try {
                refresh(RefreshMode.DEFAULT, FeatureRefreshSource.POLLING);
            } catch (FeatureFetchException e) {
                log.error("FeatureFetchException occur with message - {}, Code is - {}", e.getMessage(), e.getErrorCode(), e);
            }
            refreshExpiresAt();
        }
    }

    /**
     * Posts the remote evaluation request and applies the response as the features payload.
     *
     * @throws FeatureFetchException when there is no response; an error status is reported to
     *                               callbacks and listeners instead
     */
    public void fetchForRemoteEval(@Nullable RequestBodyForRemoteEval body, FeatureRefreshSource source) throws FeatureFetchException {
        long startedAtNanos = System.nanoTime();
        String json = GrowthBookJsonUtils.getInstance().gson.toJson(body != null ? body : new RequestBodyForRemoteEval());
        TransportResponse response;
        try {
            response = transport.postJson(endpoints.remoteEval(), json);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            FeatureFetchException failure = new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, e.getMessage());
            reportFailure(failure, source, startedAtNanos);
            throw failure;
        }
        if (response.code() == HttpURLConnection.HTTP_OK) {
            handleFeaturesResponse(response, source, startedAtNanos);
        } else {
            reportFailure(new Throwable("Response is not success, response code is:" + response.code() + ". And message is: " + response.message()), source, startedAtNanos);
        }
    }

    /**
     * Stops polling, server-sent events and scheduled refreshes, drops callbacks and listeners,
     * and releases the cache manager. Held features stay readable.
     */
    public void shutdown() {
        shuttingDown.set(true);
        callbacks.clear();
        notifier.clear();
        refreshScheduler.shutdown();
        poller.stop();
        sse.close();
        cache.release();
    }

    public FeatureSnapshot snapshot() {
        return snapshot.get();
    }

    public boolean hasFeatureData() {
        return hasFeatureData.get();
    }

    public int activeFeatureCount() {
        return snapshot.get().getParsedFeatures().size();
    }

    public FeatureRefreshStrategy strategy() {
        return strategy;
    }

    /**
     * @return the epoch second the held features go stale, as a live value
     */
    public AtomicLong expiresAt() {
        return expiresAt;
    }

    /**
     * @return whether {@link #initialize} has completed, as a live value
     */
    public AtomicBoolean initialized() {
        return initialized;
    }

    public RefreshMetrics metrics() {
        return metrics;
    }

    public FeatureCacheStore cache() {
        return cache;
    }

    public FeatureTransport transport() {
        return transport;
    }

    public boolean isSseAllowed() {
        return sseAllowed.get();
    }

    public boolean isSseConnected() {
        return sse.isConnected() && !shuttingDown.get();
    }

    public int sseRetryAttempts() {
        return sse.retryAttempts();
    }

    /**
     * Registers a legacy callback; the type is deprecated but still public API.
     */
    @SuppressWarnings("deprecation")
    public void addCallback(FeatureRefreshCallback callback) {
        callbacks.add(callback);
    }

    public void clearCallbacks() {
        callbacks.clear();
    }

    public void notifyCallbacksOfRefresh(String featuresJson) {
        callbacks.notifyRefresh(featuresJson);
    }

    public void notifyCallbacksOfError(Throwable error) {
        callbacks.notifyError(error);
    }

    public void addListener(@Nullable FeatureRefreshListener listener) {
        if (listener != null) {
            notifier.add(listener);
        }
    }

    public void removeListener(@Nullable FeatureRefreshListener listener) {
        if (listener != null) {
            notifier.remove(listener);
        }
    }

    /**
     * Applies a features response as if it had just been fetched.
     *
     * @return whether the payload differs from the held one
     * @throws FeatureFetchException when the response is unusable
     */
    public boolean applyResponse(String responseJson, boolean fromCache) throws FeatureFetchException {
        PayloadParser.Payload payload = parser.parse(responseJson);
        if (!fromCache) {
            cache.saveQuietly(responseJson);
        }
        return applyPayload(payload, fromCache);
    }

    private void fetchOnce(RefreshMode mode, FeatureRefreshSource source, long startedAtNanos) throws FeatureFetchException {
        TransportResponse response;
        try {
            response = transport.get(endpoints.features(), requestHeaders(mode));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RetryableFeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, e.getMessage(), e);
        }
        sseAllowed.set(SSE_ENABLED.equals(response.header(HttpHeaders.X_SSE_SUPPORT.getHeader())));
        handleFeaturesResponse(response, source, startedAtNanos);
    }

    private Map<String, String> requestHeaders(RefreshMode mode) {
        if (!endpoints.supportsConditionalRequests()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new HashMap<>();
        if (mode == RefreshMode.FORCE) {
            headers.put(HttpHeaders.CACHE_CONTROL.getHeader(), "no-cache");
            return headers;
        }
        String eTag = eTags.get(endpoints.features());
        if (eTag != null) {
            headers.put(HttpHeaders.IF_NONE_MATCH.getHeader(), eTag);
        }
        headers.put(HttpHeaders.CACHE_CONTROL.getHeader(), "max-age=" + swrTtlSeconds.getAsInt());
        return headers;
    }

    private void handleFeaturesResponse(TransportResponse response, FeatureRefreshSource source, long startedAtNanos) throws FeatureFetchException {
        if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            log.info("Features not modified (304). Using existing data.");
            refreshExpiresAt();
            callbacks.notifyRefresh(snapshot.get().getFeaturesJson());
            reportSuccess(source, false, startedAtNanos);
            return;
        }
        if (response.code() != HttpURLConnection.HTTP_OK) {
            throw statusError(response);
        }
        String body = response.body();
        if (body == null) {
            throw new RetryableFeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, "Feature response body was null");
        }
        boolean featuresChanged = applyResponse(body, false);
        rememberETag(response.header(ETAG));
        reportSuccess(source, featuresChanged, startedAtNanos);
    }

    /**
     * Remembered only after the payload applied: an ETag for a payload that failed would make the
     * server answer 304 and the new features would never load.
     */
    private void rememberETag(@Nullable String eTag) {
        if (eTag != null && endpoints.supportsConditionalRequests()) {
            eTags.put(endpoints.features(), eTag);
        }
    }

    private static FeatureFetchException statusError(TransportResponse response) {
        log.error("FeatureFetchException: HTTP_RESPONSE_ERROR with status {}, response: {}", response.code(), response.body());
        String message = "responded with status " + response.code();
        return FeatureFetchHttpStatus.isRetryable(response.code())
                ? new RetryableFeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR, message)
                : new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR, message);
    }

    /**
     * After every attempt failed: reports the error, falls back to the cache when there are no
     * features yet, and rethrows when there still are none. The failure is recorded and
     * listeners are notified either way, including when it is rethrown.
     */
    private void handleFetchFailure(FeatureFetchException failure, FeatureRefreshSource source, long startedAtNanos) throws FeatureFetchException {
        boolean hadFeatureData = hasFeatureData.get();
        Throwable eventError = failure.getCause() == null ? failure : failure.getCause();
        metrics.recordError(failure);
        AtomicBoolean cachedFeaturesChanged = new AtomicBoolean();
        try {
            FeatureFetchFailureHandler.handle(
                    failure,
                    callbacks::notifyError,
                    hasFeatureData::get,
                    () -> loadCachedFeatures(cachedFeaturesChanged));
        } finally {
            boolean loadedFromCache = !hadFeatureData && hasFeatureData.get();
            metrics.recordFailure(loadedFromCache);
            notifier.notifyFailure(eventError, source, loadedFromCache && cachedFeaturesChanged.get(), loadedFromCache,
                    FeatureRefreshNotifier.elapsedMillis(startedAtNanos));
        }
    }

    private boolean shouldSkipRefresh(RefreshMode mode) {
        return FeatureRefreshCacheFreshness.shouldSkipRefresh(
                mode == RefreshMode.FORCE,
                backgroundFetchInterval,
                metrics::lastSuccessfulFetchAtMillis,
                () -> FeatureRefreshCacheFreshness.timestampMillisOrUnknown(cache.lastUpdatedMillis()),
                hasFeatureData::get,
                () -> loadCachedFeatures(new AtomicBoolean()),
                clock::millis);
    }

    /**
     * @param changedOut set to whether the cached payload differs from the held one, which is not
     *                   the same as whether it loaded
     * @return whether there are features after the attempt
     */
    private boolean loadCachedFeatures(AtomicBoolean changedOut) {
        String cached = cache.load();
        if (cached == null) {
            return false;
        }
        try {
            changedOut.set(applyResponse(cached, true));
            return hasFeatureData.get();
        } catch (FeatureFetchException | RuntimeException e) {
            log.warn("Failed to load cached features.", e);
            return false;
        }
    }

    /**
     * Publishes the payload as one atomic snapshot, so readers never pair this payload's features
     * with another payload's saved groups.
     */
    private boolean applyPayload(PayloadParser.Payload payload, boolean fromCache) {
        FeatureSnapshot previous = snapshot.get();
        boolean changed = !Objects.equals(previous.getFeaturesJson(), payload.features())
                || !Objects.equals(previous.getSavedGroupsJson(), payload.savedGroups());
        Map<String, Feature<?>> features = TransformationUtil.transformFeatures(payload.features());
        JsonObject savedGroups = TransformationUtil.transformSavedGroups(payload.savedGroups());
        snapshot.set(FeatureSnapshot.of(
                payload.features(),
                payload.savedGroups(),
                features != null ? features : Collections.<String, Feature<?>>emptyMap(),
                savedGroups != null ? savedGroups : new JsonObject()));
        hasFeatureData.set(true);
        if (!fromCache) {
            metrics.recordFetched();
            callbacks.notifyRefresh(payload.features());
        }
        refreshExpiresAt();
        return changed;
    }

    /**
     * Without {@code X-Sse-Support: enabled} on the features response the server doesn't stream,
     * so the repository falls back to stale-while-revalidate refreshing instead.
     */
    private void startSse(boolean retryOnFailure) {
        if (sseAllowed.get()) {
            sse.start(retryOnFailure);
            return;
        }
        log.info("Falling back to stale-while-revalidate refresh strategy. 'X-Sse-Support: enabled' not present on resource returned at {}", endpoints.features());
        strategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        if (swrRefresh == SwrRefresh.POLLING) {
            poller.start(swrTtlSeconds.getAsInt());
        }
    }

    private void reportSuccess(FeatureRefreshSource source, boolean featuresChanged, long startedAtNanos) {
        metrics.recordSuccess(false);
        notifier.notifySuccess(source, featuresChanged, false, FeatureRefreshNotifier.elapsedMillis(startedAtNanos));
    }

    private void reportFailure(Throwable error, FeatureRefreshSource source, long startedAtNanos) {
        metrics.recordError(error);
        metrics.recordFailure(false);
        callbacks.notifyError(error);
        notifier.notifyFailure(error, source, false, false, FeatureRefreshNotifier.elapsedMillis(startedAtNanos));
    }

    private void refreshExpiresAt() {
        expiresAt.set(Instant.now(clock).getEpochSecond() + swrTtlSeconds.getAsInt());
    }

    private boolean isExpired() {
        return Instant.now(clock).getEpochSecond() >= expiresAt.get();
    }

    private final class SseHandler implements SseSession.Handler {
        @Override
        public void onFeatures(String featuresJson) throws FeatureFetchException {
            long startedAtNanos = System.nanoTime();
            try {
                reportSuccess(FeatureRefreshSource.SSE, applyResponse(featuresJson, false), startedAtNanos);
            } catch (FeatureFetchException e) {
                metrics.recordError(e);
                metrics.recordFailure(false);
                notifier.notifyFailure(e, FeatureRefreshSource.SSE, false, false, FeatureRefreshNotifier.elapsedMillis(startedAtNanos));
                throw e;
            }
        }

        /**
         * Reported on both the legacy callback and the listener channel, so listener-only
         * subscribers such as the remote-eval cache invalidation see it.
         */
        @Override
        public void onFeaturesChanged() {
            callbacks.notifyRefresh(snapshot.get().getFeaturesJson());
            metrics.recordSuccess(false);
            notifier.notifySuccess(FeatureRefreshSource.SSE, false, false, 0L);
        }

        @Override
        public void beforeReconnect() {
            try {
                refresh(RefreshMode.FORCE, FeatureRefreshSource.MANUAL);
            } catch (FeatureFetchException e) {
                log.error("Failed to fetch features while reconnecting SSE.", e);
            }
        }
    }
}
