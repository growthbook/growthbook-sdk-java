package growthbook.sdk.java.cache.redis;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Shared configuration for the Redis cache adapters (key namespacing, optional TTL, clock).
 *
 * <p>Client-specific options (the Jedis pool or Lettuce connection) are added by subclasses. The
 * builder uses a self-type so the shared setters return the concrete builder, keeping the fluent
 * chain order-independent.
 */
public abstract class AbstractRedisCacheOptions {

    public static final String DEFAULT_KEY_PREFIX = "growthbook:features:";

    private final Clock clock;
    private final Duration ttl;
    private final String keyPrefix;

    protected AbstractRedisCacheOptions(AbstractBuilder<?> builder) {
        this.keyPrefix = builder.keyPrefix;
        this.ttl = builder.ttl;
        this.clock = builder.clock;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Duration getTtl() {
        return ttl;
    }

    public Clock getClock() {
        return clock;
    }

    /**
     * Base builder holding the options shared by all Redis clients.
     *
     * @param <B> the concrete builder type, returned by the shared setters
     */
    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private Duration ttl;
        private Clock clock = Clock.systemUTC();

        /**
         * @return {@code this}, typed as the concrete builder
         */
        protected abstract B self();

        /**
         * Prefix applied to every Redis key so the adapter stays within its own namespace.
         * Defaults to {@value #DEFAULT_KEY_PREFIX}. {@code clearCache()} only removes keys under
         * this prefix.
         */
        public B keyPrefix(String keyPrefix) {
            if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
                throw new IllegalArgumentException("keyPrefix must not be null or blank");
            }
            this.keyPrefix = keyPrefix;
            return self();
        }

        /**
         * Optional time-to-live applied to each entry on write. {@code null} (default) means entries
         * do not expire.
         */
        public B ttl(Duration ttl) {
            if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
                throw new IllegalArgumentException("ttl must be greater than 0");
            }
            this.ttl = ttl;
            return self();
        }

        public B clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return self();
        }
    }
}
