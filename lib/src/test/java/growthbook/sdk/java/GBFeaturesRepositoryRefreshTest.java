package growthbook.sdk.java;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GBFeaturesRepositoryRefreshTest {
    private static final String FEATURES_RESPONSE = "{\"features\":{\"test\":{\"defaultValue\":true}}}";
    private static final String CACHED_RESPONSE = "{\"features\":{\"cached\":{\"defaultValue\":true}}}";
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

    @Test
    void forceRefreshBypassesFreshCacheCheck() throws Exception {
        OkHttpClient httpClient = successfulHttpClient();
        GbCacheManager cacheManager = cacheManagerWithTimestamp(System.currentTimeMillis());
        GBFeaturesRepository repository = repositoryBuilder(httpClient, cacheManager)
                .backgroundFetchInterval(Duration.ofHours(48))
                .build();

        repository.initialize();
        repository.refreshFeatures(RefreshMode.FORCE);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient, times(1)).newCall(requestCaptor.capture());
        List<Request> requests = requestCaptor.getAllValues();
        assertEquals("no-cache", requests.get(0).header("Cache-Control"));
        assertNull(requests.get(0).header("If-None-Match"));

        repository.shutdown();
    }

    @Test
    void retrySucceedsAfterInitialNetworkFailure() throws Exception {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        AtomicInteger attempts = new AtomicInteger();
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation ->
                attempts.incrementAndGet() == 1 ? failedCall() : successfulCall()
        );
        GBFeaturesRepository repository = repositoryBuilder(httpClient).build();

        repository.fetchFeatures();

        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        verify(httpClient, times(2)).newCall(any(Request.class));
    }

    @Test
    void retryExhaustionUsesCachedFeaturesAndStops() throws Exception {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation -> failedCall());
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        when(cacheManager.loadCache(anyString())).thenReturn(CACHED_RESPONSE);
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .okHttpClient(httpClient)
                .cacheManager(cacheManager)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .build();

        repository.fetchFeatures();

        assertEquals("{\"cached\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        verify(httpClient, times(5)).newCall(any(Request.class));
        verify(cacheManager, times(1)).loadCache(anyString());
    }

    @Test
    void failedRefreshKeepsExistingFeatureDataInsteadOfLoadingOlderCache() throws Exception {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        AtomicInteger attempts = new AtomicInteger();
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation ->
                attempts.incrementAndGet() == 1 ? successfulCall() : failedCall()
        );
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        when(cacheManager.loadCache(anyString())).thenReturn(CACHED_RESPONSE);
        GBFeaturesRepository repository = repositoryBuilder(httpClient, cacheManager).build();

        repository.initialize();
        repository.refreshFeatures(RefreshMode.FORCE);

        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        verify(httpClient, times(6)).newCall(any(Request.class));
        verify(cacheManager, never()).loadCache(anyString());
        repository.shutdown();
    }

    @Test
    void backgroundForceRefreshReturnsImmediatelyAndRefreshesFeatures() throws Exception {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch refreshCompleted = new CountDownLatch(1);
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation ->
                blockingSuccessfulCall(refreshStarted, releaseRefresh)
        );
        GBFeaturesRepository repository = repositoryBuilder(httpClient).build();
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
        assertTrue(refreshStarted.await(1, TimeUnit.SECONDS), "Background refresh should start the network request");

        releaseRefresh.countDown();

        assertTrue(refreshCompleted.await(1, TimeUnit.SECONDS), "Background refresh should update features");
        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        repository.shutdown();
    }

    @Test
    void freshCacheSkipsDefaultRefreshNetworkCall() throws Exception {
        OkHttpClient httpClient = successfulHttpClient();
        GbCacheManager cacheManager = cacheManagerWithTimestamp(System.currentTimeMillis());
        GBFeaturesRepository repository = repositoryBuilder(httpClient, cacheManager)
                .backgroundFetchInterval(Duration.ofHours(48))
                .build();

        repository.initialize();

        assertEquals("{\"cached\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        verify(httpClient, never()).newCall(any(Request.class));
        repository.shutdown();
    }

    @Test
    void staleCacheTriggersDefaultRefreshNetworkCall() throws Exception {
        OkHttpClient httpClient = successfulHttpClient();
        GbCacheManager cacheManager = cacheManagerWithTimestamp(
                System.currentTimeMillis() - Duration.ofHours(2).toMillis()
        );
        GBFeaturesRepository repository = repositoryBuilder(httpClient, cacheManager)
                .backgroundFetchInterval(Duration.ofHours(1))
                .build();

        repository.initialize();

        assertEquals("{\"test\":{\"defaultValue\":true}}", repository.getFeaturesJson());
        verify(httpClient, times(1)).newCall(any(Request.class));
        repository.shutdown();
    }

    @Test
    void existingBuilderInitializationKeepsNewOptionsDisabledByDefault() {
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .clientKey("sdk-abc123")
                .build();

        assertNull(repository.getBackgroundFetchInterval());
        assertEquals(FeatureFetchRetryPolicy.DEFAULT_MAX_ATTEMPTS, repository.getRetryPolicy().getMaxAttempts());
    }

    @Test
    void sseReconnectDoesNotScheduleWhenRetryOnFailureIsDisabled() throws Exception {
        GBFeaturesRepository repository = repositoryWithSlowSseRetryPolicy(3);

        try {
            invokeScheduleSseReconnect(repository, false);

            assertEquals(0, getSseRetryAttempts(repository).get());
            assertNull(getField(repository, "sseRetryScheduler"));
        } finally {
            repository.shutdown();
        }
    }

    @Test
    void sseReconnectAttemptsAreBounded() throws Exception {
        int maxAttempts = 3;
        GBFeaturesRepository repository = repositoryWithSlowSseRetryPolicy(maxAttempts);

        try {
            invokeScheduleSseReconnect(repository, true);
            getSseReconnectScheduled(repository).set(false);
            invokeScheduleSseReconnect(repository, true);
            getSseReconnectScheduled(repository).set(false);
            invokeScheduleSseReconnect(repository, true);
            getSseReconnectScheduled(repository).set(false);
            invokeScheduleSseReconnect(repository, true);

            assertEquals(maxAttempts, getSseRetryAttempts(repository).get());
        } finally {
            repository.shutdown();
        }
    }

    private static GBFeaturesRepository.GBFeaturesRepositoryBuilder repositoryBuilder(OkHttpClient httpClient) {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .okHttpClient(httpClient)
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private static GBFeaturesRepository.GBFeaturesRepositoryBuilder repositoryBuilder(
            OkHttpClient httpClient,
            GbCacheManager cacheManager
    ) {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .okHttpClient(httpClient)
                .cacheManager(cacheManager)
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private static GbCacheManager cacheManagerWithTimestamp(long timestampMillis) {
        GbCacheManager cacheManager = mock(GbCacheManager.class);
        when(cacheManager.loadCache(anyString())).thenReturn(CACHED_RESPONSE);
        when(cacheManager.getLastUpdatedMillis(anyString())).thenReturn(timestampMillis);
        return cacheManager;
    }

    private static GBFeaturesRepository repositoryWithSlowSseRetryPolicy(int maxAttempts) {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(new FeatureFetchRetryPolicy(
                        maxAttempts,
                        Duration.ofHours(1),
                        Duration.ofHours(1)
                ))
                .build();
    }

    private static void invokeScheduleSseReconnect(GBFeaturesRepository repository, boolean retryOnFailure) throws Exception {
        Method method = GBFeaturesRepository.class.getDeclaredMethod("scheduleSseReconnect", Boolean.class);
        method.setAccessible(true);
        method.invoke(repository, retryOnFailure);
    }

    private static AtomicInteger getSseRetryAttempts(GBFeaturesRepository repository) throws Exception {
        return getField(repository, "sseRetryAttempts");
    }

    private static AtomicBoolean getSseReconnectScheduled(GBFeaturesRepository repository) throws Exception {
        return getField(repository, "sseReconnectScheduled");
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(GBFeaturesRepository repository, String fieldName) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(repository);
    }

    private static OkHttpClient successfulHttpClient() throws IOException {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        when(httpClient.newCall(any(Request.class))).thenAnswer(invocation -> successfulCall());
        return httpClient;
    }

    private static Call successfulCall() throws IOException {
        Call call = mock(Call.class);
        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://localhost").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("ETag", "v1")
                .body(ResponseBody.create(FEATURES_RESPONSE, MediaType.parse("application/json")))
                .build();
        when(call.execute()).thenReturn(response);
        return call;
    }

    private static Call blockingSuccessfulCall(
            CountDownLatch refreshStarted,
            CountDownLatch releaseRefresh
    ) throws IOException {
        Call call = mock(Call.class);
        when(call.execute()).thenAnswer(invocation -> {
            refreshStarted.countDown();
            releaseRefresh.await(2, TimeUnit.SECONDS);
            return successfulResponse();
        });
        return call;
    }

    private static Response successfulResponse() {
        return new Response.Builder()
                .request(new Request.Builder().url("http://localhost").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("ETag", "v1")
                .body(ResponseBody.create(FEATURES_RESPONSE, MediaType.parse("application/json")))
                .build();
    }

    private static Call failedCall() throws IOException {
        Call call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("network failure"));
        return call;
    }
}
