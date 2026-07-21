package growthbook.sdk.java.diagnostics.model;

import growthbook.sdk.java.repository.FeatureRefreshStrategy;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;

/**
 * Feature refresh metadata.
 */
@Value
@Builder
public class RefreshDiagnostics {
    /**
     * Active feature refresh strategy.
     */
    FeatureRefreshStrategy strategy;

    /**
     * Epoch-millis timestamp of the last successful feature refresh.
     */
    @Nullable
    Long lastSuccessfulRefreshAtMillis;

    /**
     * Age in milliseconds since the last successful feature refresh.
     */
    @Nullable
    Long lastSuccessfulRefreshAgeMillis;

    /**
     * Epoch-millis timestamp when the current feature cache expires.
     */
    @Nullable
    Long nextCacheExpiresAtMillis;

    /**
     * Milliseconds until the current cache expires. Negative values mean it is already stale.
     */
    @Nullable
    Long millisUntilCacheExpiry;

    /**
     * Whether the current feature data is past its cache expiry.
     */
    boolean stale;

    /**
     * Number of successful refresh events observed by the active repository.
     */
    long successCount;

    /**
     * Number of failed refresh events observed by the active repository.
     */
    long failureCount;

    /**
     * Number of failed refresh events since the last successful refresh event.
     */
    long consecutiveFailures;

    /**
     * Epoch-millis timestamp of the last failed refresh event.
     */
    @Nullable
    Long lastFailureAtMillis;

    /**
     * Whether the last refresh event loaded feature data from cache.
     */
    @Nullable
    Boolean lastLoadedFromCache;

    /**
     * Most recent refresh/init error, sanitized for diagnostics output.
     */
    @Nullable
    ErrorDiagnostics lastError;
}
