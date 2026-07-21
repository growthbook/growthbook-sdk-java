package growthbook.sdk.java.featurefetch;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Decides whether a non-forced feature refresh can skip the network request
 * because cached or in-memory feature data is still fresh enough.
 */
public final class FeatureRefreshCacheFreshness {
    private static final long UNKNOWN_TIMESTAMP_MILLIS = 0L;

    private FeatureRefreshCacheFreshness() {
    }

    public static boolean shouldSkipRefresh(
            boolean forceRefresh,
            Duration backgroundFetchInterval,
            LongSupplier lastSuccessfulFetchAtMillis,
            LongSupplier cacheLastUpdatedMillis,
            BooleanSupplier hasFeatureData,
            BooleanSupplier loadCachedFeatures
    ) {
        if (forceRefresh || backgroundFetchInterval == null) {
            return false;
        }

        long intervalMillis = backgroundFetchInterval.toMillis();
        if (intervalMillis <= 0) {
            return false;
        }

        long newestFeatureDataAtMillis = lastSuccessfulFetchAtMillis.getAsLong();
        long cachedAtMillis = cacheLastUpdatedMillis.getAsLong();
        if (cachedAtMillis > UNKNOWN_TIMESTAMP_MILLIS) {
            newestFeatureDataAtMillis = Math.max(newestFeatureDataAtMillis, cachedAtMillis);
        }

        boolean isFresh = newestFeatureDataAtMillis > 0
                && System.currentTimeMillis() - newestFeatureDataAtMillis < intervalMillis;
        if (!isFresh) {
            return false;
        }

        return hasFeatureData.getAsBoolean() || loadCachedFeatures.getAsBoolean();
    }

    /**
     * Converts nullable cache timestamps into the primitive form used by the
     * freshness check.
     */
    public static long timestampMillisOrUnknown(Long timestampMillis) {
        return timestampMillis == null ? UNKNOWN_TIMESTAMP_MILLIS : timestampMillis;
    }
}
