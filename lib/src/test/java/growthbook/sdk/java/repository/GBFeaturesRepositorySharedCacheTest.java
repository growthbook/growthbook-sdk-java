package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cross-instance behavior of the opt-in process-shared payload cache, asserted against real request
 * counts on a loopback {@link HttpServer}. Covers the acceptance criteria: two instances → one fetch,
 * concurrent inits → one fetch, prefetch → zero extra fetches, distinct keys isolated, and the
 * opt-out default preserving per-instance fetching.
 */
class GBFeaturesRepositorySharedCacheTest {

    private static final String FEATURES_BODY = "{\"features\":{\"my-flag\":{\"defaultValue\":true}}}";

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<GBFeaturesRepository> repositories = new ArrayList<>();
    private volatile long responseDelayMillis = 0L;

    @BeforeEach
    void setUp() throws IOException {
        GBFeaturesRepository.clearSharedPayloadState();
        requestCount.set(0);
        responseDelayMillis = 0L;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/features/", exchange -> {
            requestCount.incrementAndGet();
            if (responseDelayMillis > 0) {
                try {
                    Thread.sleep(responseDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = FEATURES_BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        for (GBFeaturesRepository repository : repositories) {
            try {
                repository.shutdown();
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        }
        repositories.clear();
        if (server != null) {
            server.stop(0);
        }
        GBFeaturesRepository.clearSharedPayloadState();
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
        // Given a slow server so both cold starts overlap in flight
        responseDelayMillis = 200L;
        GBFeaturesRepository first = sharedRepository("sdk-abc");
        GBFeaturesRepository second = sharedRepository("sdk-abc");

        // When both instances initialize in parallel, released together
        CountDownLatch start = new CountDownLatch(1);
        Thread t1 = initThread(first, start);
        Thread t2 = initThread(second, start);
        t1.start();
        t2.start();
        start.countDown();
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

    private void awaitRequestCount(int expected) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadlineNanos) {
            if (requestCount.get() >= expected) {
                return;
            }
            Thread.yield();
        }
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
