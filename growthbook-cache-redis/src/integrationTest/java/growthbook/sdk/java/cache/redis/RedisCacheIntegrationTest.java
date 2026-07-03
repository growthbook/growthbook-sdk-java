package growthbook.sdk.java.cache.redis;

import growthbook.sdk.java.cache.redis.jedis.JedisGbCacheManager;
import growthbook.sdk.java.cache.redis.lettuce.LettuceGbCacheManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests exercising the Jedis and Lettuce adapters against a real Redis instance.
 * Requires Docker; run with {@code ./gradlew :growthbook-cache-redis:integrationTest}.
 */
@Testcontainers
class RedisCacheIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private JedisPool jedisPool;
    private RedisClient lettuceClient;
    private StatefulRedisConnection<String, String> lettuceConnection;

    @BeforeEach
    void setUp() {
        String host = REDIS.getHost();
        int port = REDIS.getMappedPort(6379);
        jedisPool = new JedisPool(host, port);
        lettuceClient = RedisClient.create("redis://" + host + ":" + port);
        lettuceConnection = lettuceClient.connect();
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        if (lettuceConnection != null) {
            lettuceConnection.close();
        }
        if (lettuceClient != null) {
            lettuceClient.shutdown();
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Test
    @DisplayName("Jedis: save then load round-trips through real Redis")
    void jedisRoundTrip() {
        JedisGbCacheManager cache = JedisGbCacheManager.create(jedisPool);

        cache.saveContent("FEATURE_CACHE.json", "{\"v\":1}");

        assertEquals("{\"v\":1}", cache.loadCache("FEATURE_CACHE.json"));
        assertNotNull(cache.getLastUpdatedMillis("FEATURE_CACHE.json"));
        assertNull(cache.loadCache("absent"));
        assertNull(cache.getLastUpdatedMillis("absent"));
    }

    @Test
    @DisplayName("Lettuce: save then load round-trips through real Redis")
    void lettuceRoundTrip() {
        LettuceGbCacheManager cache = LettuceGbCacheManager.create(lettuceConnection);

        cache.saveContent("FEATURE_CACHE.json", "{\"v\":2}");

        assertEquals("{\"v\":2}", cache.loadCache("FEATURE_CACHE.json"));
        assertNotNull(cache.getLastUpdatedMillis("FEATURE_CACHE.json"));
        assertNull(cache.loadCache("absent"));
        assertNull(cache.getLastUpdatedMillis("absent"));
    }

    @Test
    @DisplayName("Cross-client: data written with Jedis is read with Lettuce (identical wire format)")
    void crossClientInterop() {
        JedisGbCacheManager.create(jedisPool).saveContent("shared", "payload");

        LettuceGbCacheManager lettuce = LettuceGbCacheManager.create(lettuceConnection);
        assertEquals("payload", lettuce.loadCache("shared"));
        assertNotNull(lettuce.getLastUpdatedMillis("shared"));
    }

    @Test
    @DisplayName("TTL: an entry written with a short TTL expires on the Redis side")
    void ttlExpiresEntry() throws InterruptedException {
        JedisGbCacheManager cache = JedisGbCacheManager.builder()
                .jedisPool(jedisPool)
                .ttl(Duration.ofSeconds(1))
                .buildManager();

        cache.saveContent("temp", "soon-gone");
        assertEquals("soon-gone", cache.loadCache("temp"));

        Thread.sleep(1500);

        assertNull(cache.loadCache("temp"));
    }

    @Test
    @DisplayName("clearCache: removes only the adapter's namespace, leaving other keys intact")
    void clearCacheRemovesOnlyOwnNamespace() {
        JedisGbCacheManager cache = JedisGbCacheManager.create(jedisPool);
        cache.saveContent("a", "1");
        cache.saveContent("b", "2");
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("unrelated:key", "keep");
        }

        cache.clearCache();

        assertNull(cache.loadCache("a"));
        assertNull(cache.loadCache("b"));
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("keep", jedis.get("unrelated:key"));
        }
    }
}
