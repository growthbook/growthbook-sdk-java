package growthbook.sdk.java.cache.redis.jedis;

import growthbook.sdk.java.cache.redis.AbstractRedisCacheOptions;
import redis.clients.jedis.JedisPool;

import java.util.Objects;

/**
 * Configuration for {@link JedisGbCacheManager}.
 *
 * <p>The caller owns the {@link JedisPool} lifecycle; the adapter only borrows connections from it.
 * Shared options (key prefix, TTL, clock) come from {@link AbstractRedisCacheOptions}.
 */
public final class JedisCacheOptions extends AbstractRedisCacheOptions {

    private final JedisPool jedisPool;

    private JedisCacheOptions(Builder builder) {
        super(builder);
        this.jedisPool = builder.jedisPool;
    }

    public static Builder builder() {
        return new Builder();
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public static final class Builder extends AbstractBuilder<Builder> {
        private JedisPool jedisPool;

        private Builder() {
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * The Jedis connection pool used to reach Redis. Required. Owned by the caller.
         */
        public Builder jedisPool(JedisPool jedisPool) {
            this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
            return this;
        }

        public JedisCacheOptions build() {
            Objects.requireNonNull(jedisPool, "jedisPool must be provided");
            return new JedisCacheOptions(this);
        }

        /**
         * Convenience terminal that builds these options into a ready-to-use cache manager.
         *
         * @return a {@link JedisGbCacheManager} configured with these options
         */
        public JedisGbCacheManager buildManager() {
            return JedisGbCacheManager.create(build());
        }
    }
}
