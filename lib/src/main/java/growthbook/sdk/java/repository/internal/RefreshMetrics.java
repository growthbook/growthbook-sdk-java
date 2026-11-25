package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.exception.FeatureFetchException;

import javax.annotation.Nullable;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Counters and timestamps of feature refreshes, for diagnostics. Thread-safe.
 */
public final class RefreshMetrics {

    private final Clock clock;
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong lastErrorAtMillis = new AtomicLong();
    private final AtomicLong consecutiveFailures = new AtomicLong();
    private final AtomicLong lastFailureAtMillis = new AtomicLong();
    private final AtomicLong lastSuccessfulFetchAtMillis = new AtomicLong();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicReference<Boolean> lastLoadedFromCache = new AtomicReference<>();

    public RefreshMetrics(Clock clock) {
        this.clock = clock;
    }

    public void recordFetched() {
        lastSuccessfulFetchAtMillis.set(clock.millis());
    }

    public void recordSuccess(boolean loadedFromCache) {
        successCount.incrementAndGet();
        consecutiveFailures.set(0);
        lastLoadedFromCache.set(loadedFromCache);
    }

    public void recordError(@Nullable Throwable error) {
        lastError.set(error != null ? error : new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN));
        lastErrorAtMillis.set(clock.millis());
    }

    public void recordFailure(boolean loadedFromCache) {
        failureCount.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        lastFailureAtMillis.set(clock.millis());
        lastLoadedFromCache.set(loadedFromCache);
    }

    public long lastSuccessfulFetchAtMillis() {
        return lastSuccessfulFetchAtMillis.get();
    }

    @Nullable
    public Throwable lastError() {
        return lastError.get();
    }

    public long lastErrorAtMillis() {
        return lastErrorAtMillis.get();
    }

    public long successCount() {
        return successCount.get();
    }

    public long failureCount() {
        return failureCount.get();
    }

    public long consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public long lastFailureAtMillis() {
        return lastFailureAtMillis.get();
    }

    @Nullable
    public Boolean lastLoadedFromCache() {
        return lastLoadedFromCache.get();
    }
}
