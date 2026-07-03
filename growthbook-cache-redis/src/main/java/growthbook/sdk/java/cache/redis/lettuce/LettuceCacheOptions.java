package growthbook.sdk.java.cache.redis.lettuce;

import growthbook.sdk.java.cache.redis.AbstractRedisCacheOptions;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.Objects;

/**
 * Configuration for {@link LettuceGbCacheManager}.
 *
 * <p>The caller owns the {@link StatefulRedisConnection} lifecycle; the adapter only issues
 * synchronous commands over it. Shared options (key prefix, TTL, clock) come from
 * {@link AbstractRedisCacheOptions}.
 */
public final class LettuceCacheOptions extends AbstractRedisCacheOptions {

    private final StatefulRedisConnection<String, String> connection;

    private LettuceCacheOptions(Builder builder) {
        super(builder);
        this.connection = builder.connection;
    }

    public static Builder builder() {
        return new Builder();
    }

    public StatefulRedisConnection<String, String> getConnection() {
        return connection;
    }

    public static final class Builder extends AbstractBuilder<Builder> {
        private StatefulRedisConnection<String, String> connection;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * The Lettuce connection used to reach Redis. Required. Owned by the caller.
         */
        public Builder connection(StatefulRedisConnection<String, String> connection) {
            this.connection = Objects.requireNonNull(connection, "connection");
            return this;
        }

        public LettuceCacheOptions build() {
            Objects.requireNonNull(connection, "connection must be provided");
            return new LettuceCacheOptions(this);
        }

        /**
         * Convenience terminal that builds these options into a ready-to-use cache manager.
         *
         * @return a {@link LettuceGbCacheManager} configured with these options
         */
        public LettuceGbCacheManager buildManager() {
            return LettuceGbCacheManager.create(build());
        }
    }
}
