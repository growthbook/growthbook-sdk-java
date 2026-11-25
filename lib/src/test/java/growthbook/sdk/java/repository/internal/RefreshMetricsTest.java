package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.testhelpers.TestClock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.exception.FeatureFetchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshMetricsTest {

    private final TestClock clock = TestClock.atMillis(1_000L);
    private final RefreshMetrics metrics = new RefreshMetrics(clock);

    @Test
    @DisplayName("Starts with no refreshes recorded")
    void startsEmpty() {
        // Given
        // When
        // Then
        assertEquals(0, metrics.successCount());
        assertEquals(0, metrics.failureCount());
        assertEquals(0, metrics.consecutiveFailures());
        assertEquals(0, metrics.lastErrorAtMillis());
        assertEquals(0, metrics.lastFailureAtMillis());
        assertEquals(0, metrics.lastSuccessfulFetchAtMillis());
        assertNull(metrics.lastError());
        assertNull(metrics.lastLoadedFromCache());
    }

    @Test
    @DisplayName("Counts consecutive failures and stamps the last failure with the clock")
    void countsConsecutiveFailures() {
        // Given
        metrics.recordFailure(false);
        clock.advanceMillis(500L);

        // When
        metrics.recordFailure(true);

        // Then
        assertEquals(2, metrics.failureCount());
        assertEquals(2, metrics.consecutiveFailures());
        assertEquals(1_500L, metrics.lastFailureAtMillis());
        assertTrue(metrics.lastLoadedFromCache());
    }

    @Test
    @DisplayName("Resets consecutive failures on success but keeps the failure count")
    void successResetsConsecutiveFailures() {
        // Given
        metrics.recordFailure(false);
        metrics.recordFailure(false);

        // When
        metrics.recordSuccess(true);

        // Then
        assertEquals(1, metrics.successCount());
        assertEquals(2, metrics.failureCount());
        assertEquals(0, metrics.consecutiveFailures());
        assertTrue(metrics.lastLoadedFromCache());
    }

    @Test
    @DisplayName("Stamps the last successful fetch with the clock")
    void stampsSuccessfulFetch() {
        // Given
        clock.advanceMillis(2_000L);

        // When
        metrics.recordFetched();

        // Then
        assertEquals(3_000L, metrics.lastSuccessfulFetchAtMillis());
    }

    @Test
    @DisplayName("Records the error and stamps it with the clock")
    void recordsErrorWithClockTimestamp() {
        // Given
        IllegalStateException error = new IllegalStateException("synthetic failure");
        clock.advanceMillis(250L);

        // When
        metrics.recordError(error);

        // Then
        assertSame(error, metrics.lastError());
        assertEquals(1_250L, metrics.lastErrorAtMillis());
    }

    @Test
    @DisplayName("Records a null error as an unknown feature fetch exception")
    void recordsNullErrorAsFeatureFetchException() {
        // Given
        // When
        metrics.recordError(null);

        // Then
        FeatureFetchException recorded = assertInstanceOf(FeatureFetchException.class, metrics.lastError());
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN, recorded.getErrorCode());
        assertEquals(1_000L, metrics.lastErrorAtMillis());
    }

    @Test
    @DisplayName("Remembers whether the latest outcome came from the cache")
    void remembersLatestLoadedFromCache() {
        // Given
        metrics.recordSuccess(true);

        // When
        metrics.recordFailure(false);

        // Then
        assertFalse(metrics.lastLoadedFromCache());
    }

}
