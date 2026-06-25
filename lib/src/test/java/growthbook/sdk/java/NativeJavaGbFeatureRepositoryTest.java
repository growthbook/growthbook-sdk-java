package growthbook.sdk.java;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.NativeJavaGbFeatureRepository;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

class NativeJavaGbFeatureRepositoryTest {
    private static final String API_HOST = "https://cdn.growthbook.io";
    private static final String CLIENT_KEY = "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8";
    private WireMockServer wireMock;
    private NativeJavaGbFeatureRepository repository;

    @BeforeEach
    void setUp() {
        repository = NativeJavaGbFeatureRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .encryptionKey(null)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(60)
                .build();

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void constructor_withNullEncryptionKey_buildsSuccessfully() {
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                "https://cdn.growthbook.io",
                "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                subject.getFeaturesEndpoint());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
    }

    @Test
    void constructor_withEncryptionKey_buildsSuccessfully() {
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                "https://cdn.growthbook.io",
                "sdk-862b5mHcP9XPugqD",
                "BhB1wORFmZLTDjbvstvS8w==",
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD",
                subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test
    void builder_withNullEncryptionKey_buildsSuccessfully() {
        NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                subject.getFeaturesEndpoint());
    }

    @Test
    void builder_withEncryptionKey_buildsSuccessfully() {
        NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-862b5mHcP9XPugqD")
                .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD",
                subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test()
    void constructor_withNullClientKey_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new NativeJavaGbFeatureRepository(
                API_HOST,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void initialize_success_setsInitializedTrue() throws Exception {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "disabled")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .isCacheDisabled(true)
                .build();

        repo.initialize();

        assertTrue(repo.getInitialized().get());
        assertNotEquals("{}", repo.getFeaturesJson());
    }

    @Test
    void initialize_calledTwice_fetchesOnce() throws Exception {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "disabled")
                        .withBody("{\"features\":{}}")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(true)
                .build();

        repo.initialize();
        repo.initialize();

        wireMock.verify(1, getRequestedFor(urlPathMatching("/api/features/.*")));
    }

    @Test
    void initialize_networkError_throwsAndNotInitialized() {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(true)
                .build();

        assertThrows(FeatureFetchException.class, repo::initialize);
        assertFalse(repo.getInitialized().get());
    }

    @Test
    void initialize_success_withServerSentEventsStrategy() throws Exception {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "enabled")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}")));

        wireMock.stubFor(get(urlPathMatching("/sub/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .isCacheDisabled(true)
                .build();

        repo.initialize();

        Thread.sleep(500);

        assertTrue(repo.getInitialized().get());
        assertNotEquals("{}", repo.getFeaturesJson());
    }

    @Test
    void initialize_success_withRemoteEvalStrategy() throws Exception {
        wireMock.stubFor(post(urlPathMatching("/api/eval/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "disabled")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .refreshStrategy(FeatureRefreshStrategy.REMOTE_EVAL_STRATEGY)
                .isCacheDisabled(true)
                .build();

        repo.initialize();

        assertTrue(repo.getInitialized().get());
        assertNotEquals("{}", repo.getFeaturesJson());
    }

    @Test
    void fetchFeatures_networkError_throwsFeatureFetchException() {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(true)
                .build();

        assertThrows(FeatureFetchException.class, repo::fetchFeatures);
    }

    @Test
    void initialize_networkError_throwsFeatureFetchException() {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(true)
                .build();

        assertThrows(FeatureFetchException.class, repo::initialize);
    }

    @Test
    void onFeaturesRefresh_whenRegistered_callsCallbackOnRefresh() {
        FeatureRefreshCallback listener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(listener);

        repository.onRefreshSuccess("{}");

        verify(listener, times(1)).onRefresh("{}");
    }

    @Test
    void onFeaturesRefresh_whenRegistered_callsCallbackOnError() {
        FeatureRefreshCallback listener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(listener);

        repository.onRefreshFailed(new Exception("Error"));

        verify(listener, times(1)).onError(any(Throwable.class));
    }

    @Test
    void clearCallbacks_whenCalled_removesAllCallbacks() {
        FeatureRefreshCallback refreshListener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(refreshListener);

        repository.clearCallbacks();

        verify(refreshListener, never()).onRefresh(anyString());
    }

    @Test
    void fetchFeatures_usesCachedData_whenNetworkFails() throws Exception {
        GbCacheManager mockCache = mock(GbCacheManager.class);
        String cachedJson = "{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}";
        when(mockCache.loadCache(anyString())).thenReturn(cachedJson);

        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(false)
                .cacheManager(mockCache)
                .build();

        repo.fetchFeatures();

        verify(mockCache).loadCache(anyString());
    }

    @Test
    void getCachedFeatures_throws_whenCacheEmpty() {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(true)
                .build();

        assertThrows(FeatureFetchException.class, repo::fetchFeatures);
    }

    @Test
    void initialize_fallsBackToSWR_whenSseSupportDisabled() throws Exception {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "disabled")
                        .withBody("{\"features\":{}}")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .isCacheDisabled(true)
                .build();

        repo.initialize();

        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, repo.getRefreshStrategy());
    }

    @Test
    void fetchFeatures_updatesFeaturesJson_whenResponseIsValid() throws Exception {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "disabled")
                        .withBody("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .isCacheDisabled(true)
                .build();

        repo.fetchFeatures();

        assertEquals("{\"dark-mode\":{\"defaultValue\":true}}", repo.getFeaturesJson());
    }

    @Test
    void getFeaturesJson_returnsEmptyJson_beforeInitialize() {
        assertEquals("{}", repository.getFeaturesJson());
    }

    @Test
    void getFeaturesJson_refreshes_whenCacheExpired() throws Exception {
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Sse-Support", "disabled")
                        .withBody("{\"features\":{\"dark-mode\":{\"defaultValue\":true}}}")));

        NativeJavaGbFeatureRepository repo = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-test123")
                .swrTtlSeconds(0)
                .isCacheDisabled(true)
                .build();

        repo.initialize();

        Thread.sleep(100);

        repo.getFeaturesJson();

        wireMock.verify(2, getRequestedFor(urlPathMatching("/api/features/.*")));
    }
}
