package growthbook.sdk.java;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.repository.NativeJavaGbFeatureRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeJavaGbFeatureRepositoryRefreshTest {
    private static final String FEATURES_RESPONSE = "{\"features\":{\"test\":{\"defaultValue\":true}}}";
    private static final String CACHED_RESPONSE = "{\"features\":{\"cached\":{\"defaultValue\":true}}}";
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

    private final Queue<Integer> responseCodes = new ConcurrentLinkedQueue<>();
    private final List<RequestHeaders> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String apiHost;
    private int defaultResponseCode;
    private CountDownLatch requestStarted;
    private CountDownLatch releaseResponse;

    @BeforeEach
    void setUp() throws IOException {
        defaultResponseCode = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/sdk-abc123", this::handleRequest);
        server.start();
        apiHost = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void forceRefreshBypassesFreshCacheCheck() throws Exception {
        GbCacheManager cacheManager = cacheManagerWithTimestamp(System.currentTimeMillis());
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .cacheManager(cacheManager)
                .backgroundFetchInterval(Duration.ofHours(48))
                .build();

        repository.initialize();
        repository.refreshFeatures(RefreshMode.FORCE);

        assertEquals(1, requests.size());
        assertEquals("no-cache", requests.get(0).cacheControl);
        assertNull(requests.get(0).ifNoneMatch);
    }

    @Test
    void retrySucceedsAfterInitialNetworkFailure() throws Exception {
        responseCodes.add(500);
        responseCodes.add(200);
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .isCacheDisabled(true)
                .build();

        repository.fetchFeatures();

        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        assertEquals(2, requests.size());
    }

    @Test
    void retryExhaustionUsesCachedFeaturesAndStops() throws Exception {
        defaultResponseCode = 500;
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        when(cacheManager.loadCache(anyString())).thenReturn(CACHED_RESPONSE);
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .cacheManager(cacheManager)
                .build();

        repository.fetchFeatures();

        assertEquals("{\"cached\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        assertEquals(5, requests.size());
        verify(cacheManager, times(1)).loadCache(anyString());
    }

    @Test
    void failedRefreshKeepsExistingFeatureDataInsteadOfLoadingOlderCache() throws Exception {
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        when(cacheManager.loadCache(anyString())).thenReturn(CACHED_RESPONSE);
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .cacheManager(cacheManager)
                .build();

        repository.initialize();
        defaultResponseCode = 500;
        repository.refreshFeatures(RefreshMode.FORCE);

        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        assertEquals(6, requests.size());
        verify(cacheManager, never()).loadCache(anyString());
    }

    @Test
    void backgroundForceRefreshReturnsImmediatelyAndRefreshesFeatures() throws Exception {
        requestStarted = new CountDownLatch(1);
        releaseResponse = new CountDownLatch(1);
        CountDownLatch refreshCompleted = new CountDownLatch(1);
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .isCacheDisabled(true)
                .build();
        repository.onFeaturesRefresh(new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                refreshCompleted.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        long startedAtMillis = System.currentTimeMillis();
        repository.requestFeatureRefresh(RefreshMode.FORCE);
        long elapsedMillis = System.currentTimeMillis() - startedAtMillis;

        assertTrue(elapsedMillis < 500, "Background refresh scheduling should not block the caller");
        assertTrue(requestStarted.await(1, TimeUnit.SECONDS), "Background refresh should start the network request");

        releaseResponse.countDown();

        assertTrue(refreshCompleted.await(1, TimeUnit.SECONDS), "Background refresh should update features");
        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        repository.shutdown();
    }

    @Test
    void freshPersistentCacheSkipsInitialNetworkCall() throws Exception {
        GbCacheManager cacheManager = cacheManagerWithTimestamp(System.currentTimeMillis());
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .cacheManager(cacheManager)
                .backgroundFetchInterval(Duration.ofHours(48))
                .build();

        repository.initialize();

        assertEquals("{\"cached\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        assertEquals(0, requests.size());
    }

    @Test
    void stalePersistentCacheTriggersInitialNetworkCall() throws Exception {
        GbCacheManager cacheManager = cacheManagerWithTimestamp(
                System.currentTimeMillis() - Duration.ofHours(2).toMillis()
        );
        NativeJavaGbFeatureRepository repository = repositoryBuilder()
                .cacheManager(cacheManager)
                .backgroundFetchInterval(Duration.ofHours(1))
                .build();

        repository.initialize();

        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        assertEquals(1, requests.size());
    }

    @Test
    void existingBuilderInitializationKeepsNewOptionsDisabledByDefault() {
        NativeJavaGbFeatureRepository repository = NativeJavaGbFeatureRepository.builder()
                .clientKey("sdk-abc123")
                .build();

        assertNull(repository.getBackgroundFetchInterval());
        assertEquals(FeatureFetchRetryPolicy.DEFAULT_MAX_ATTEMPTS, repository.getRetryPolicy().getMaxAttempts());
    }

    private NativeJavaGbFeatureRepository.NativeJavaGbFeatureRepositoryBuilder repositoryBuilder() {
        return NativeJavaGbFeatureRepository.builder()
                .apiHost(apiHost)
                .clientKey("sdk-abc123")
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private static GbCacheManager cacheManagerWithTimestamp(long timestampMillis) {
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        when(cacheManager.loadCache(anyString())).thenReturn(CACHED_RESPONSE);
        when(cacheManager.getLastUpdatedMillis(anyString())).thenReturn(timestampMillis);
        return cacheManager;
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        requests.add(new RequestHeaders(
                exchange.getRequestHeaders().getFirst("Cache-Control"),
                exchange.getRequestHeaders().getFirst("If-None-Match")
        ));
        if (requestStarted != null) {
            requestStarted.countDown();
        }
        if (releaseResponse != null) {
            try {
                releaseResponse.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Integer queuedResponseCode = responseCodes.poll();
        int responseCode = queuedResponseCode == null ? defaultResponseCode : queuedResponseCode;
        byte[] response = (responseCode == 200 ? FEATURES_RESPONSE : "{\"error\":\"failed\"}")
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("X-Sse-Support", "disabled");
        if (responseCode == 200) {
            exchange.getResponseHeaders().add("ETag", "v1");
        }
        exchange.sendResponseHeaders(responseCode, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static final class RequestHeaders {
        private final String cacheControl;
        private final String ifNoneMatch;

        private RequestHeaders(String cacheControl, String ifNoneMatch) {
            this.cacheControl = cacheControl;
            this.ifNoneMatch = ifNoneMatch;
        }
    }
}
