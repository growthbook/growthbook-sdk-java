package growthbook.sdk.java.cache.redis.stickybucket;

import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LettuceAsyncStickyBucketServiceTest {

    private static final long TIMEOUT_SECONDS = 10;

    private final FakeRedis redis = new FakeRedis();
    @SuppressWarnings("unchecked")
    private final StatefulRedisConnection<String, String> connection = mock(StatefulRedisConnection.class);
    @SuppressWarnings("unchecked")
    private final RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class, redis);

    @BeforeEach
    void setUp() {
        when(connection.async()).thenReturn(commands);
    }

    @Test
    @DisplayName("Verify: getAssignments deserializes the stored document for a present key")
    void getAssignmentsReturnsStoredDocument() throws Exception {
        // Given
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");
        redis.data.put("id||123", json(doc));
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        StickyAssignmentsDocument result = await(service.getAssignments("id", "123"));

        // Then
        assertEquals(doc, result);
    }

    @Test
    @DisplayName("Verify: getAssignments completes with null for a missing key")
    void getAssignmentsCompletesWithNullForMissingKey() throws Exception {
        // Given
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // Then
        assertNull(await(service.getAssignments("id", "123")));
    }

    @Test
    @DisplayName("Verify: getAssignments completes with null for null attribute inputs without touching Redis")
    void getAssignmentsCompletesWithNullForNullAttributeInputs() throws Exception {
        // Given
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // Then
        assertNull(await(service.getAssignments(null, "123")));
        assertNull(await(service.getAssignments("id", null)));
        verifyNoInteractions(commands);
    }

    @Test
    @DisplayName("Verify: a Redis failure delivered through the command's future fails the stage with StickyBucketServiceException")
    void getAssignmentsFailsTheStageWhenRedisFails() {
        // Given
        redis.failCommandsWith = new RedisException("boom");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        CompletionStage<StickyAssignmentsDocument> stage = service.getAssignments("id", "123");

        // Then
        assertFailsWithStickyBucketServiceException(stage);
    }

    @Test
    @DisplayName("Verify: a failure thrown while issuing a command fails the stage instead of escaping the call")
    void getAssignmentsFailsTheStageWhenIssuingTheCommandThrows() {
        // Given
        redis.throwOnIssue = new RedisException("connection closed");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        CompletionStage<StickyAssignmentsDocument> stage = service.getAssignments("id", "123");

        // Then
        assertFailsWithStickyBucketServiceException(stage);
    }

    @Test
    @DisplayName("Verify: getAssignments fails the stage with StickyBucketServiceException for a malformed payload")
    void getAssignmentsFailsTheStageOnMalformedPayload() {
        // Given
        redis.data.put("id||123", "}not-json{");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // Then
        assertFailsWithStickyBucketServiceException(service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: saveAssignments installs the JSON-encoded document under the user key without a TTL by default")
    void saveAssignmentsWritesJsonUnderKey() throws Exception {
        // Given
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        await(service.saveAssignments(doc));

        // Then
        assertEquals(doc, parse(redis.data.get("id||123")));
        assertEquals("nx", redis.lastMode);
        assertEquals("0", redis.lastTtlArg);
    }

    @Test
    @DisplayName("Verify: saveAssignments passes the configured TTL in milliseconds and applies the key prefix")
    void saveAssignmentsAppliesTtlAndPrefix() throws Exception {
        // Given
        LettuceAsyncStickyBucketService service =
                new LettuceAsyncStickyBucketService(connection, "gb:sticky:", Duration.ofDays(30));

        // When
        await(service.saveAssignments(document("id", "123", "exp__0", "control")));

        // Then
        assertTrue(redis.data.containsKey("gb:sticky:id||123"));
        assertEquals(Long.toString(Duration.ofDays(30).toMillis()), redis.lastTtlArg);
        assertEquals(document("id", "123", "exp__0", "control"), await(service.getAssignments("id", "123")));
    }

    @Test
    @DisplayName("Verify: saveAssignments keeps the stored assignments and lets the incoming ones win on conflict")
    void saveAssignmentsMergesOntoTheStoredDocument() throws Exception {
        // Given
        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("exp__0", "control");
        stored.put("other__0", "a");
        redis.data.put("id||123", json(new StickyAssignmentsDocument("id", "123", stored)));
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        await(service.saveAssignments(document("id", "123", "exp__0", "treatment")));

        // Then
        Map<String, String> merged = parse(redis.data.get("id||123")).getAssignments();
        assertEquals("treatment", merged.get("exp__0"));
        assertEquals("a", merged.get("other__0"));
        assertEquals("cas", redis.lastMode);
    }

    @Test
    @DisplayName("Verify: saveAssignments re-reads and re-merges when a competing writer wins the compare-and-set")
    void saveAssignmentsRetriesAfterLosingTheCompareAndSet() throws Exception {
        // Given a competing writer that changes the key just before the first compare-and-set
        redis.beforeCompareAndSet = attempt -> {
            if (attempt == 1) {
                redis.data.put("id||123", json(document("id", "123", "other__0", "a")));
            }
        };
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        await(service.saveAssignments(document("id", "123", "exp__0", "control")));

        // Then both writers' assignments survive
        Map<String, String> merged = parse(redis.data.get("id||123")).getAssignments();
        assertEquals("control", merged.get("exp__0"));
        assertEquals("a", merged.get("other__0"));
        assertEquals(2, redis.compareAndSets.get());
    }

    @Test
    @DisplayName("Verify: saveAssignments fails the stage after 5 lost compare-and-set attempts")
    void saveAssignmentsGivesUpAfterMaxAttempts() {
        // Given a competing writer that changes the key before every compare-and-set
        redis.beforeCompareAndSet = attempt ->
                redis.data.put("id||123", json(document("id", "123", "other__0", "v" + attempt)));
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        CompletionStage<Void> stage = service.saveAssignments(document("id", "123", "exp__0", "control"));

        // Then
        assertFailsWithStickyBucketServiceException(stage);
        assertEquals(5, redis.compareAndSets.get());
    }

    @Test
    @DisplayName("Verify: saveAssignments ignores a null document or missing key attributes")
    void saveAssignmentsIgnoresNullDocumentOrAttributes() throws Exception {
        // Given
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        await(service.saveAssignments(null));
        await(service.saveAssignments(new StickyAssignmentsDocument(null, "123", new HashMap<>())));
        await(service.saveAssignments(new StickyAssignmentsDocument("id", null, new HashMap<>())));

        // Then
        verifyNoInteractions(commands);
    }

    @Test
    @DisplayName("Verify: saveAssignments fails the stage for a document without an assignments map")
    void saveAssignmentsFailsTheStageWithoutAnAssignmentsMap() {
        // Given
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        CompletionStage<Void> stage = service.saveAssignments(new StickyAssignmentsDocument("id", "123", null));

        // Then
        assertFailsWithStickyBucketServiceException(stage);
        verifyNoInteractions(commands);
    }

    @Test
    @DisplayName("Verify: saveAssignments fails the stage with StickyBucketServiceException when Redis fails")
    void saveAssignmentsFailsTheStageWhenRedisFails() {
        // Given
        redis.failCommandsWith = new RedisException("boom");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // Then
        assertFailsWithStickyBucketServiceException(
                service.saveAssignments(document("id", "123", "exp__0", "control")));
    }

    @Test
    @DisplayName("Verify: getAllAssignments reads every key in one MGET and maps hits by their logical key")
    void getAllAssignmentsReadsInBulkKeyedByLogicalKey() throws Exception {
        // Given
        StickyAssignmentsDocument idDoc = document("id", "123", "exp__0", "control");
        StickyAssignmentsDocument cookieDoc = document("cookie", "abc", "exp__1", "variation");
        redis.data.put("id||123", json(idDoc));
        redis.data.put("cookie||abc", json(cookieDoc));
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("id", "123");
        attributes.put("missing", "x");
        attributes.put("cookie", "abc");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // When
        Map<String, StickyAssignmentsDocument> result = await(service.getAllAssignments(attributes));

        // Then
        assertEquals(2, result.size());
        assertEquals(idDoc, result.get("id||123"));
        assertEquals(cookieDoc, result.get("cookie||abc"));
        assertEquals(Collections.singletonList(Arrays.asList("id||123", "missing||x", "cookie||abc")), redis.bulkReads);
        assertEquals(0, redis.singleReads.get());
    }

    @Test
    @DisplayName("Verify: getAllAssignments completes with an empty map for null or empty attributes without touching Redis")
    void getAllAssignmentsCompletesEmptyForNullOrEmptyAttributes() throws Exception {
        // Given
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // Then
        assertTrue(await(service.getAllAssignments(null)).isEmpty());
        assertTrue(await(service.getAllAssignments(Collections.<String, String>emptyMap())).isEmpty());
        verifyNoInteractions(commands);
    }

    @Test
    @DisplayName("Verify: getAllAssignments fails the stage with StickyBucketServiceException when Redis fails")
    void getAllAssignmentsFailsTheStageWhenRedisFails() {
        // Given
        redis.failCommandsWith = new RedisException("boom");
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);

        // Then
        assertFailsWithStickyBucketServiceException(
                service.getAllAssignments(Collections.singletonMap("id", "123")));
    }

    @Test
    @DisplayName("Verify: the constructor rejects a null connection and a zero, negative or sub-millisecond TTL")
    void constructorRejectsInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new LettuceAsyncStickyBucketService(null));
        assertThrows(IllegalArgumentException.class,
                () -> new LettuceAsyncStickyBucketService(connection, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new LettuceAsyncStickyBucketService(connection, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new LettuceAsyncStickyBucketService(connection, Duration.ofNanos(1)));
    }

    @Test
    @Timeout(30)
    @DisplayName("Verify: concurrent saves for one user never erase each other's assignments")
    void concurrentSavesForOneUserAllSurvive() throws Exception {
        // Given as many writers as a save has attempts, so each can lose to every other and still land
        int writers = 5;
        LettuceAsyncStickyBucketService service = new LettuceAsyncStickyBucketService(connection);
        CyclicBarrier start = new CyclicBarrier(writers);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            // When
            List<Future<Void>> saves = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                String experimentKey = "exp-" + i + "__0";
                saves.add(pool.submit(() -> {
                    start.await();
                    return await(service.saveAssignments(document("id", "123", experimentKey, "control")));
                }));
            }
            for (Future<Void> save : saves) {
                save.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            // Then
            Map<String, String> stored = parse(redis.data.get("id||123")).getAssignments();
            assertEquals(writers, stored.size(), "every writer's assignment must survive: " + stored);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Verify: the multi-user client's async evaluation reads and writes sticky assignments through the service")
    void asyncEvaluationPersistsAndReusesAssignments() throws Exception {
        // Given a client wired to the non-blocking service
        Experiment<String> experiment = Experiment.<String>builder()
                .key("exp")
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .meta(new ArrayList<>(Arrays.asList(
                        VariationMeta.builder().key("control").build(),
                        VariationMeta.builder().key("treatment").build())))
                .build();
        UserContext user = UserContext.builder().attributesJson("{\"id\":\"u1\"}").build();
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .asyncStickyBucketService(new LettuceAsyncStickyBucketService(connection))
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        try {
            // When a new user is evaluated
            ExperimentResult<String> first = client.runAsync(experiment, user).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            client.flushStickyBucketSaves().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Then the assignment is stored under the wire-compatible key
            assertEquals(first.getKey(), parse(redis.data.get("id||u1")).getAssignments().get("exp__0"));

            // And when the store holds the other variation, async and sync evaluation both serve it
            String other = "control".equals(first.getKey()) ? "treatment" : "control";
            redis.data.put("id||u1", json(document("id", "u1", "exp__0", other)));
            GrowthBookClient secondInstance = new GrowthBookClient(Options.builder()
                    .asyncStickyBucketService(new LettuceAsyncStickyBucketService(connection))
                    .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                    .build());
            try {
                ExperimentResult<String> async =
                        secondInstance.runAsync(experiment, user).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                ExperimentResult<String> sync = secondInstance.run(experiment, user);

                assertEquals(other, async.getKey());
                assertTrue(Boolean.TRUE.equals(async.getStickyBucketUsed()));
                assertEquals(other, sync.getKey());
            } finally {
                secondInstance.shutdown();
            }
        } finally {
            client.shutdown();
        }
    }

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void assertFailsWithStickyBucketServiceException(CompletionStage<?> stage) {
        ExecutionException error = assertThrows(ExecutionException.class, () -> await(stage));
        assertTrue(error.getCause() instanceof StickyBucketServiceException,
                "expected StickyBucketServiceException, got: " + error.getCause());
    }

    private static StickyAssignmentsDocument document(String attributeName, String attributeValue,
                                                      String experimentKey, String variationKey) {
        Map<String, String> assignments = new LinkedHashMap<>();
        assignments.put(experimentKey, variationKey);
        return new StickyAssignmentsDocument(attributeName, attributeValue, assignments);
    }

    private static String json(StickyAssignmentsDocument doc) {
        return GrowthBookJsonUtils.getInstance().gson.toJson(doc);
    }

    private static StickyAssignmentsDocument parse(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, StickyAssignmentsDocument.class);
    }

    /** Hook invoked with the 1-based compare-and-set attempt number, before the attempt is applied. */
    private interface CompareAndSetHook {
        void before(int attempt);
    }

    /**
     * In-memory stand-in for the three Redis commands the service issues ({@code GET}, {@code MGET}
     * and the compare-and-set {@code EVAL}), used as the default answer of the mocked commands.
     */
    private static final class FakeRedis implements Answer<Object> {
        final Map<String, String> data = new ConcurrentHashMap<>();
        final List<List<String>> bulkReads = Collections.synchronizedList(new ArrayList<List<String>>());
        final AtomicInteger singleReads = new AtomicInteger();
        final AtomicInteger compareAndSets = new AtomicInteger();
        volatile RuntimeException failCommandsWith;
        volatile RuntimeException throwOnIssue;
        volatile CompareAndSetHook beforeCompareAndSet;
        volatile String lastMode;
        volatile String lastTtlArg;

        @Override
        public Object answer(InvocationOnMock invocation) {
            if (throwOnIssue != null) {
                throw throwOnIssue;
            }
            Object[] arguments = invocation.getArguments();
            switch (invocation.getMethod().getName()) {
                case "get":
                    singleReads.incrementAndGet();
                    return future(data.get((String) arguments[0]));
                case "mget":
                    List<String> keys = new ArrayList<>();
                    List<KeyValue<String, String>> values = new ArrayList<>();
                    for (Object key : arguments) {
                        keys.add((String) key);
                        values.add(KeyValue.fromNullable((String) key, data.get((String) key)));
                    }
                    bulkReads.add(keys);
                    return future(values);
                case "eval":
                    return future(compareAndSet(((String[]) arguments[2])[0],
                            (String) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6]));
                default:
                    throw new UnsupportedOperationException(invocation.getMethod().getName());
            }
        }

        private Long compareAndSet(String key, String mode, String expected, String updated, String ttlArg) {
            CompareAndSetHook hook = beforeCompareAndSet;
            int attempt = compareAndSets.incrementAndGet();
            if (hook != null) {
                hook.before(attempt);
            }
            lastMode = mode;
            lastTtlArg = ttlArg;
            boolean written = "nx".equals(mode)
                    ? data.putIfAbsent(key, updated) == null
                    : data.replace(key, expected, updated);
            return written ? 1L : 0L;
        }

        private <T> RedisFuture<T> future(T value) {
            CompletedRedisFuture<T> future = new CompletedRedisFuture<>();
            if (failCommandsWith != null) {
                future.completeExceptionally(failCommandsWith);
            } else {
                future.complete(value);
            }
            return future;
        }
    }

    private static final class CompletedRedisFuture<T> extends CompletableFuture<T> implements RedisFuture<T> {
        @Override
        public String getError() {
            return null;
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
