package growthbook.sdk.java.cache.redis;

import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.GbCacheManagerContractTest;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Runs the {@link GbCacheManager} contract against the client-independent Redis algorithm, backed by
 * an in-memory keyspace that matches keys with Redis glob semantics. The Jedis and Lettuce
 * translations of the storage primitives are covered by their own mock-based tests.
 */
class AbstractRedisGbCacheManagerContractTest extends GbCacheManagerContractTest {

    private static final String KEY_PREFIX = "gb[test]:";
    private static final String GLOB_LOOKALIKE_PREFIX = "gbt:";

    private InMemoryRedisGbCacheManager cacheManager;

    @Override
    protected GbCacheManager createCacheManager() {
        cacheManager = new InMemoryRedisGbCacheManager(KEY_PREFIX, clock());
        return cacheManager;
    }

    @Override
    protected ForeignStore sharedForeignStore() {
        return new ForeignStore() {
            @Override
            public void write(String key, String value) {
                cacheManager.writeHash(GLOB_LOOKALIKE_PREFIX + key, RedisCacheEntry.toHash(value, 0L), null);
            }

            @Override
            public String read(String key) {
                return cacheManager.readHashField(GLOB_LOOKALIKE_PREFIX + key, RedisCacheEntry.FIELD_DATA);
            }
        };
    }

    @Override
    protected boolean failStorage() {
        cacheManager.failing = true;
        return true;
    }

    private static final class InMemoryRedisGbCacheManager extends AbstractRedisGbCacheManager {

        private final Map<String, Map<String, String>> keyspace = new ConcurrentHashMap<>();
        private volatile boolean failing;

        InMemoryRedisGbCacheManager(String keyPrefix, Clock clock) {
            super(keyPrefix, null, clock);
        }

        @Override
        protected void writeHash(String redisKey, Map<String, String> hash, Long ttlMillis) {
            failIfBroken();
            keyspace.put(redisKey, new HashMap<>(hash));
        }

        @Override
        protected Map<String, String> readHash(String redisKey) {
            failIfBroken();
            return keyspace.get(redisKey);
        }

        @Override
        protected String readHashField(String redisKey, String field) {
            failIfBroken();
            Map<String, String> hash = keyspace.get(redisKey);
            return hash == null ? null : hash.get(field);
        }

        @Override
        protected void deleteByPrefix(String matchPattern) {
            failIfBroken();
            Pattern pattern = globToRegex(matchPattern);
            keyspace.keySet().removeIf(key -> pattern.matcher(key).matches());
        }

        private void failIfBroken() {
            if (failing) {
                throw new IllegalStateException("simulated Redis connection failure");
            }
        }

        private static Pattern globToRegex(String glob) {
            StringBuilder regex = new StringBuilder();
            boolean inClass = false;
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '\\' && i + 1 < glob.length()) {
                    appendLiteral(regex, glob.charAt(++i));
                } else if (inClass) {
                    if (c == ']') {
                        regex.append(']');
                        inClass = false;
                    } else if (c == '^' || c == '-') {
                        regex.append(c);
                    } else {
                        appendLiteral(regex, c);
                    }
                } else if (c == '[') {
                    regex.append('[');
                    inClass = true;
                } else if (c == '*') {
                    regex.append(".*");
                } else if (c == '?') {
                    regex.append('.');
                } else {
                    appendLiteral(regex, c);
                }
            }
            return Pattern.compile(regex.toString(), Pattern.DOTALL);
        }

        private static void appendLiteral(StringBuilder regex, char c) {
            if (!Character.isLetterOrDigit(c)) {
                regex.append('\\');
            }
            regex.append(c);
        }
    }
}
