package growthbook.sdk.java.cache.caffeine;

import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for {@link CaffeineGbCacheManager}.
 */
public final class CaffeineCacheOptions {

    public static final long DEFAULT_MAXIMUM_SIZE = 1000L;

    private final Clock clock;
    private final Ticker ticker;
    private final long maximumSize;
    private final Long maximumWeight;
    private final boolean recordStats;
    private final Duration expireAfterWrite;
    private final Duration expireAfterAccess;

    private CaffeineCacheOptions(Builder builder) {
        this.maximumSize = builder.maximumSize;
        this.maximumWeight = builder.maximumWeight;
        this.expireAfterWrite = builder.expireAfterWrite;
        this.expireAfterAccess = builder.expireAfterAccess;
        this.recordStats = builder.recordStats;
        this.clock = builder.clock;
        this.ticker = builder.ticker;
    }

    public static CaffeineCacheOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    /**
     * @return the maximum total payload weight (in bytes) before size-based eviction, or
     * {@code null} when entry-count bounding ({@link #getMaximumSize()}) is used instead
     */
    public Long getMaximumWeight() {
        return maximumWeight;
    }

    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public boolean isRecordStats() {
        return recordStats;
    }

    public Clock getClock() {
        return clock;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public static final class Builder {
        private long maximumSize = DEFAULT_MAXIMUM_SIZE;
        private Long maximumWeight;
        private Duration expireAfterWrite;
        private Duration expireAfterAccess;
        private boolean recordStats;
        private Clock clock = Clock.systemUTC();
        private Ticker ticker;

        private Builder() {
        }

        /**
         * Bounds the cache by entry count. Ignored when {@link #maximumWeight(long)} is set, which
         * takes precedence.
         */
        public Builder maximumSize(long maximumSize) {
            if (maximumSize <= 0) {
                throw new IllegalArgumentException("maximumSize must be greater than 0");
            }
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * Bounds the cache by total payload weight in bytes (UTF-8 length of cached values).
         *
         * <p>This is the recommended bound for the GrowthBook feature cache: it stores a single
         * large JSON payload per endpoint, so entry-count limits via {@link #maximumSize(long)} do
         * not meaningfully constrain memory. When set, {@code maximumWeight} takes precedence over
         * {@code maximumSize}.
         */
        public Builder maximumWeight(long maximumWeight) {
            if (maximumWeight <= 0) {
                throw new IllegalArgumentException("maximumWeight must be greater than 0");
            }
            this.maximumWeight = maximumWeight;
            return this;
        }

        public Builder expireAfterWrite(Duration expireAfterWrite) {
            if (expireAfterWrite != null && (expireAfterWrite.isZero() || expireAfterWrite.isNegative())) {
                throw new IllegalArgumentException("expireAfterWrite must be greater than 0");
            }
            this.expireAfterWrite = expireAfterWrite;
            return this;
        }

        /**
         * Evicts an entry when it has not been accessed for the given duration.
         *
         * <p>Note: cache reads count as accesses. Because the SDK reads through
         * {@code loadCache} and {@code getLastUpdatedMillis} (both lookups), each read resets this
         * timer — an entry expires only after it has been idle (neither written nor read) for the
         * configured duration.
         */
        public Builder expireAfterAccess(Duration expireAfterAccess) {
            if (expireAfterAccess != null && (expireAfterAccess.isZero() || expireAfterAccess.isNegative())) {
                throw new IllegalArgumentException("expireAfterAccess must be greater than 0");
            }
            this.expireAfterAccess = expireAfterAccess;
            return this;
        }

        public Builder recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = Objects.requireNonNull(ticker, "ticker");
            return this;
        }

        public CaffeineCacheOptions build() {
            return new CaffeineCacheOptions(this);
        }

        /**
         * Convenience terminal that builds these options into a ready-to-use cache manager.
         *
         * @return a {@link CaffeineGbCacheManager} configured with these options
         */
        public CaffeineGbCacheManager buildManager() {
            return CaffeineGbCacheManager.create(build());
        }
    }
}
