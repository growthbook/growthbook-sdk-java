package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-instance behavior of the opt-in process-shared payload cache, asserted against real request
 * counts on a loopback {@link HttpServer}. Covers the acceptance criteria: two instances → one fetch,
 * concurrent inits → one fetch, prefetch → zero extra fetches, distinct keys isolated, and the
 * opt-out default preserving per-instance fetching.
 *
 * <p>Concurrency is exercised deterministically — no {@code Thread.sleep} or timing assumptions. The
 * server can hold its response on a latch, so a test releases it only once both callers are provably
 * inside the single-flight ({@link #awaitSingleFlightParticipants}); this gating is why a plain
 * {@code HttpServer} is used rather than WireMock, which cannot block a response until N concurrent
 * callers have arrived.
 */
class GBFeaturesRepositorySharedCacheTest {

    private static final String FEATURES_BODY = "{\"features\":{\"my-flag\":{\"defaultValue\":true}}}";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<GBFeaturesRepository> repositories = new ArrayList<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>(FEATURES_BODY);
    private volatile int responseStatus = 200;
    /** When set, each request counts this down on arrival so a test can await the leader reaching the server. */
    private volatile CountDownLatch requestArrived;
    /** When set, each request blocks on this before responding so a test can hold the response open. */
    private volatile CountDownLatch releaseResponse;

    @BeforeEach
    void setUp() throws IOException {
        GBFeaturesRepository.clearSharedPayloadState();
        requestCount.set(0);
        responseBody.set(FEATURES_BODY);
        responseStatus = 200;
        requestArrived = null;
        releaseResponse = null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newFixedThreadPool(4);
        server.setExecutor(serverExecutor);
        server.createContext("/api/features/", exchange -> {
            requestCount.incrementAndGet();
            CountDownLatch arrived = requestArrived;
            if (arrived != null) {
                arrived.countDown();
            }
            CountDownLatch release = releaseResponse;
            if (release != null) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            int status = responseStatus;
            byte[] body = (status == 200 ? responseBody.get() : "{\"error\":\"boom\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        // Tear down every resource, then surface the first shutdown failure instead of swallowing it.
        RuntimeException firstFailure = null;
        for (GBFeaturesRepository repository : repositories) {
            try {
                repository.shutdown();
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        repositories.clear();
        if (releaseResponse != null) {
            releaseResponse.countDown();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        GBFeaturesRepository.clearSharedPayloadState();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @Test
    @DisplayName("Verify: two instances with the same key trigger exactly one network fetch")
    void twoInstancesSameKeyTriggerSingleFetch() throws FeatureFetchException {
        // Given two repositories sharing one apiHost + clientKey, both opted into the shared cache
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        GBFeaturesRepository second = sharedRepository("sdk-abc");

        // When both initialize
        first.initialize();
        second.initialize();

        // Then only one request reached the server and both hold the features
        assertEquals(1, requestCount.get());
        assertTrue(first.getFeaturesJson().contains("my-flag"));
        assertTrue(second.getFeaturesJson().contains("my-flag"));
    }

    @Test
    @DisplayName("Verify: concurrent inits for the same key de-duplicate to a single in-flight fetch")
    void concurrentInitsSameKeyDedupeToSingleFetch() throws InterruptedException {
        // Given the server holds its response until released, so the leader's fetch stays in flight
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        requestArrived = arrived;
        releaseResponse = release;
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        GBFeaturesRepository second = sharedRepository("sdk-abc");

        // When both instances initialize in parallel, released together
        CountDownLatch start = new CountDownLatch(1);
        Thread t1 = initThread(first, start);
        Thread t2 = initThread(second, start);
        t1.start();
        t2.start();
        start.countDown();

        // Hold the leader's fetch until both callers are provably inside the single-flight, then release
        assertTrue(arrived.await(5, TimeUnit.SECONDS), "leader fetch never reached the server");
        awaitSingleFlightParticipants(2);
        release.countDown();
        t1.join(5000);
        t2.join(5000);

        // Then the concurrent cold starts collapsed to a single fetch
        assertEquals(1, requestCount.get());
        assertTrue(first.getFeaturesJson().contains("my-flag"));
        assertTrue(second.getFeaturesJson().contains("my-flag"));
    }

    @Test
    @DisplayName("Verify: a prefetch lets a later instance serve from the shared cache with zero extra fetches")
    void prefetchThenInstanceServesFromSharedCache() throws FeatureFetchException {
        // Given a boot-time prefetch that warms the shared cache (one fetch)
        GBFeaturesRepository.prefetchPayload(
                PrefetchOptions.builder()
                        .apiHost(apiHost())
                        .clientKey("sdk-abc")
                        .build()
        );
        assertEquals(1, requestCount.get());

        // When a later instance with the same key initializes
        GBFeaturesRepository repository = sharedRepository("sdk-abc");
        repository.initialize();

        // Then it served from the shared cache and added no network fetch
        assertEquals(1, requestCount.get());
        assertTrue(repository.getFeaturesJson().contains("my-flag"));
    }

    @Test
    @DisplayName("Verify: distinct keys stay isolated and each triggers its own fetch")
    void distinctKeysRemainIsolated() throws FeatureFetchException {
        // Given two repositories with different client keys
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        GBFeaturesRepository second = sharedRepository("sdk-xyz");

        // When both initialize
        first.initialize();
        second.initialize();

        // Then each performed its own fetch
        assertEquals(2, requestCount.get());
    }

    @Test
    @DisplayName("Verify: without opting in, each instance fetches independently (per-instance default preserved)")
    void sharingDisabledByDefaultEachInstanceFetches() throws FeatureFetchException {
        // Given two repositories that did NOT opt into the shared cache
        GBFeaturesRepository first = plainRepository("sdk-abc");
        GBFeaturesRepository second = plainRepository("sdk-abc");

        // When both initialize
        first.initialize();
        second.initialize();

        // Then the shared cache was never consulted — each fetched on its own
        assertEquals(2, requestCount.get());
    }

    @Test
    @DisplayName("Verify: prefetch rejects options without a client key")
    void prefetchRejectsMissingClientKey() {
        // Given options missing the required client key
        PrefetchOptions options = PrefetchOptions.builder().apiHost(apiHost()).build();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> GBFeaturesRepository.prefetchPayload(options));
    }

    @Test
    @DisplayName("Verify: a stale shared entry is served immediately and revalidated in the background")
    void staleEntryIsServedAndRevalidatedInBackground() throws FeatureFetchException {
        // Given a zero stale TTL so any stored entry is immediately stale (but not expired)
        GBFeaturesRepository.configureSharedCache(Duration.ZERO, Duration.ofMinutes(5), 10);
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        first.initialize();
        assertEquals(1, requestCount.get());

        // When a second instance initializes against the now-stale entry
        GBFeaturesRepository second = sharedRepository("sdk-abc");
        second.initialize();

        // Then it was served the cached payload immediately...
        assertTrue(second.getFeaturesJson().contains("my-flag"));
        // ...and a single background revalidation fetch followed
        awaitRequestCount(2);
        assertEquals(2, requestCount.get());
    }

    @Test
    @DisplayName("Verify: an entry past its max age is discarded and the next instance refetches")
    void expiredEntryBeyondMaxAgeRefetches() throws FeatureFetchException {
        // Given a zero max age so any stored entry is immediately expired
        GBFeaturesRepository.configureSharedCache(Duration.ZERO, Duration.ZERO, 10);
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        GBFeaturesRepository second = sharedRepository("sdk-abc");

        // When both initialize
        first.initialize();
        second.initialize();

        // Then the expired entry is never served — each performed its own fetch
        assertEquals(2, requestCount.get());
    }

    @Test
    @DisplayName("Verify: prefetch with skipCache forces a fetch even when a fresh entry exists")
    void prefetchWithSkipCacheForcesFetch() throws FeatureFetchException {
        // Given a first prefetch that warms a fresh entry (default 60s stale TTL)
        GBFeaturesRepository.prefetchPayload(
                PrefetchOptions.builder().apiHost(apiHost()).clientKey("sdk-abc").build()
        );
        assertEquals(1, requestCount.get());

        // When a second prefetch opts to skip the cache
        GBFeaturesRepository.prefetchPayload(
                PrefetchOptions.builder().apiHost(apiHost()).clientKey("sdk-abc").skipCache(true).build()
        );

        // Then it fetched again despite the fresh entry
        assertEquals(2, requestCount.get());
    }

    @Test
    @DisplayName("Verify: a background reload de-duplicated onto another instance still updates this one")
    void backgroundReloadFollowerAdoptsRefreshedPayload() throws InterruptedException, FeatureFetchException {
        // Given a stale (but not expired) shared entry holding v1
        GBFeaturesRepository.configureSharedCache(Duration.ZERO, Duration.ofMinutes(5), 10);
        responseBody.set("{\"features\":{\"v1-flag\":{\"defaultValue\":true}}}");
        GBFeaturesRepository warm = sharedRepository("sdk-abc");
        warm.initialize();
        assertEquals(1, requestCount.get());

        // and the server now serves v2 but holds the response until both reloads are in flight
        responseBody.set("{\"features\":{\"v2-flag\":{\"defaultValue\":true}}}");
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        requestArrived = arrived;
        releaseResponse = release;

        // When two instances initialize concurrently: each serves stale v1 and schedules a background reload
        GBFeaturesRepository a = sharedRepository("sdk-abc");
        GBFeaturesRepository b = sharedRepository("sdk-abc");
        CountDownLatch start = new CountDownLatch(1);
        Thread t1 = initThread(a, start);
        Thread t2 = initThread(b, start);
        t1.start();
        t2.start();
        start.countDown();
        t1.join(5000);
        t2.join(5000);

        // Hold the leader's background reload until the follower has joined the same single-flight, then release
        assertTrue(arrived.await(5, TimeUnit.SECONDS), "leader background reload never reached the server");
        awaitSingleFlightParticipants(2);
        release.countDown();

        // Then the two background reloads collapsed to a single network fetch (1 warm + 1 reload)...
        awaitRequestCount(2);
        assertEquals(2, requestCount.get());
        // ...and both instances adopted the refreshed v2 payload — the follower was not left on v1
        awaitFeatures(a, "v2-flag");
        awaitFeatures(b, "v2-flag");
        assertTrue(a.getFeaturesJson().contains("v2-flag"));
        assertTrue(b.getFeaturesJson().contains("v2-flag"));
    }

    @Test
    @DisplayName("Verify: a shared-cache hit notifies the legacy feature-refresh callback")
    void sharedCacheHitNotifiesLegacyCallback() throws FeatureFetchException {
        // Given a warmed fresh shared entry
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        first.initialize();
        assertEquals(1, requestCount.get());

        // and a second instance with a legacy FeatureRefreshCallback registered before init
        GBFeaturesRepository second = sharedRepository("sdk-abc");
        AtomicReference<String> delivered = new AtomicReference<>();
        second.onFeaturesRefresh(new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                delivered.set(featuresJson);
            }

            @Override
            public void onError(Throwable throwable) {
                // not expected in this scenario
            }
        });

        // When it serves from the shared cache (no extra network fetch)
        second.initialize();

        // Then the legacy callback received the shared payload
        assertEquals(1, requestCount.get());
        assertTrue(delivered.get() != null && delivered.get().contains("my-flag"));
    }

    private void awaitFeatures(GBFeaturesRepository repository, String token) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadlineNanos) {
            String json = repository.getFeaturesJson();
            if (json != null && json.contains(token)) {
                return;
            }
            Thread.yield();
        }
    }

    private void awaitRequestCount(int expected) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadlineNanos) {
            if (requestCount.get() >= expected) {
                return;
            }
            Thread.yield();
        }
    }

    /**
     * Blocks until at least {@code expected} callers are inside the shared single-flight (a leader plus
     * its waiting followers), so a gated test can release the held response only once the followers have
     * joined — making de-duplication deterministic without any {@code Thread.sleep}.
     */
    private void awaitSingleFlightParticipants(int expected) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadlineNanos) {
            if (SharedFeatureCache.SHARED.fetchParticipantCount() >= expected) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("Timed out waiting for " + expected + " single-flight participants");
    }

    private String apiHost() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private GBFeaturesRepository sharedRepository(String clientKey) {
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost(apiHost())
                .clientKey(clientKey)
                .isCacheDisabled(true)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .sharedCacheEnabled(true)
                .build();
        repositories.add(repository);
        return repository;
    }

    private GBFeaturesRepository plainRepository(String clientKey) {
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost(apiHost())
                .clientKey(clientKey)
                .isCacheDisabled(true)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .build();
        repositories.add(repository);
        return repository;
    }

    private static Thread initThread(GBFeaturesRepository repository, CountDownLatch start) {
        return new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                repository.initialize();
            } catch (FeatureFetchException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
