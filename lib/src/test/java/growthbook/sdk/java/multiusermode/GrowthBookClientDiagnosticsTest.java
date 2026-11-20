package growthbook.sdk.java.multiusermode;

import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createDefaultOptions;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockBuilder;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import growthbook.sdk.java.Version;
import growthbook.sdk.java.diagnostics.model.Diagnostics;
import growthbook.sdk.java.diagnostics.model.enums.CacheState;
import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsHealthState;
import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsIssueCode;
import growthbook.sdk.java.diagnostics.model.enums.FeatureDataSource;
import growthbook.sdk.java.diagnostics.model.enums.FeatureState;
import growthbook.sdk.java.diagnostics.model.enums.StreamingState;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.CacheMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GrowthBookClientDiagnosticsTest {

    private static final String FETCH_ERROR_MESSAGE = "temporary outage for custom_key";

    private MockedStatic<GBFeaturesRepository> repositoryStatics;
    private GBFeaturesRepository repository;
    private GBFeaturesRepository.GBFeaturesRepositoryBuilder repositoryBuilder;

    @BeforeEach
    void setUp() {
        repository = createMockRepository();
        repositoryBuilder = createMockBuilder(repository);
        repositoryStatics = mockStatic(GBFeaturesRepository.class);
        repositoryStatics.when(GBFeaturesRepository::builder).thenReturn(repositoryBuilder);
    }

    @AfterEach
    void tearDown() {
        repositoryStatics.close();
    }

    @Test
    @DisplayName("Reports a not-ready client with masked configuration before initialization")
    void reportsNotReadyClientBeforeInitialization() {
        // Given
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertTrue(diagnostics.getGeneratedAtMillis() > 0);
        assertEquals(Version.SDK_VERSION, diagnostics.getSdk().getVersion());
        assertEquals(DiagnosticsHealthState.NOT_READY, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.CLIENT_NOT_INITIALIZED), issueCodes(diagnostics));
        assertEquals("https://custom.growthbook.io", diagnostics.getConfig().getApiHost());
        assertEquals("cust..._key", diagnostics.getConfig().getClientKeyMasked());
        assertEquals("https://custom.growthbook.io/api/features/cust..._key", diagnostics.getConfig().getFeaturesEndpoint());
        assertEquals("https://custom.growthbook.io/sub/cust..._key", diagnostics.getConfig().getEventsEndpoint());
        assertTrue(diagnostics.getConfig().isEncryptionConfigured());
        assertEquals(60, diagnostics.getConfig().getSwrTtlSeconds());
        assertNull(diagnostics.getConfig().getBackgroundFetchIntervalMillis());
        assertEquals(5, diagnostics.getConfig().getRetryMaxAttempts());
        assertTrue(diagnostics.getConfig().isEnabled());
        assertFalse(diagnostics.getConfig().isQaMode());
        assertFalse(diagnostics.getConfig().isStickyBucketingEnabled());
        assertFalse(diagnostics.getClient().isInitialized());
        assertFalse(diagnostics.getClient().isShutdown());
        assertFalse(diagnostics.getClient().isRemoteEvalEnabled());
        assertEquals(FeatureState.NOT_LOADED, diagnostics.getFeatures().getState());
        assertEquals(FeatureDataSource.NONE, diagnostics.getFeatures().getSource());
        assertFalse(diagnostics.getFeatures().isAvailable());
        assertEquals(0, diagnostics.getFeatures().getActiveFeatureCount());
        assertEquals(0, diagnostics.getFeatures().getTotalFeatureCount());
        assertFalse(diagnostics.getFeatures().getKeys().isAvailable());
        assertTrue(diagnostics.getFeatures().getKeys().getSample().isEmpty());
        assertNull(diagnostics.getFeatures().getKeys().getSha256());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, diagnostics.getRefresh().getStrategy());
        assertNull(diagnostics.getRefresh().getLastSuccessfulRefreshAtMillis());
        assertNull(diagnostics.getRefresh().getLastSuccessfulRefreshAgeMillis());
        assertNull(diagnostics.getRefresh().getNextCacheExpiresAtMillis());
        assertNull(diagnostics.getRefresh().getMillisUntilCacheExpiry());
        assertFalse(diagnostics.getRefresh().isStale());
        assertEquals(0L, diagnostics.getRefresh().getSuccessCount());
        assertEquals(0L, diagnostics.getRefresh().getFailureCount());
        assertEquals(0L, diagnostics.getRefresh().getConsecutiveFailures());
        assertNull(diagnostics.getRefresh().getLastFailureAtMillis());
        assertNull(diagnostics.getRefresh().getLastLoadedFromCache());
        assertNull(diagnostics.getRefresh().getLastError());
        assertTrue(diagnostics.getCache().isEnabled());
        assertEquals(CacheState.NOT_INITIALIZED, diagnostics.getCache().getState());
        assertEquals(CacheMode.AUTO, diagnostics.getCache().getMode());
        assertEquals(0L, diagnostics.getCache().getLastUpdatedMillis());
        assertNull(diagnostics.getCache().getAgeMillis());
        assertEquals(StreamingState.DISABLED, diagnostics.getStreaming().getState());
        assertFalse(diagnostics.getRemoteEval().isEnabled());
        assertFalse(diagnostics.getRemoteEval().isReady());
    }

    @Test
    @DisplayName("Serializes diagnostics with the masked client key and without any key in clear")
    void serializesDiagnosticsWithoutKeysInClear() {
        // Given
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions());

        // When
        String json = client.getDiagnostics().toJson();

        // Then
        assertTrue(json.contains("\"config\""));
        assertTrue(json.contains("cust..._key"));
        assertFalse(json.contains("custom_key"));
        assertFalse(json.contains("test_key"));
    }

    @Test
    @DisplayName("Reports the repository's feature, refresh, cache and streaming state after initialization")
    void reportsRepositoryStateAfterInitialization() {
        // Given
        long nowMillis = System.currentTimeMillis();
        long lastFetchMillis = nowMillis - TimeUnit.SECONDS.toMillis(1);
        long expiresAtSeconds = TimeUnit.MILLISECONDS.toSeconds(nowMillis + TimeUnit.SECONDS.toMillis(60));
        long cacheUpdatedMillis = nowMillis - 500L;
        when(repository.getLastSuccessfulFetchAtMillis()).thenReturn(lastFetchMillis);
        when(repository.getExpiresAt()).thenReturn(expiresAtSeconds);
        when(repository.getCacheLastUpdatedMillis()).thenReturn(cacheUpdatedMillis);
        GrowthBookClient client = initializedClient(createDefaultOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        long generatedAt = diagnostics.getGeneratedAtMillis();
        assertEquals(DiagnosticsHealthState.READY, diagnostics.getHealth().getState());
        assertEquals(Collections.<DiagnosticsIssueCode>emptyList(), issueCodes(diagnostics));
        assertEquals("https://custom.growthbook.io", diagnostics.getConfig().getApiHost());
        assertEquals("cust..._key", diagnostics.getConfig().getClientKeyMasked());
        assertEquals("https://custom.growthbook.io/api/features/cust..._key", diagnostics.getConfig().getFeaturesEndpoint());
        assertEquals("https://custom.growthbook.io/sub/cust..._key", diagnostics.getConfig().getEventsEndpoint());
        assertTrue(diagnostics.getConfig().isEncryptionConfigured());
        assertEquals(60, diagnostics.getConfig().getSwrTtlSeconds());
        assertEquals(5, diagnostics.getConfig().getRetryMaxAttempts());
        assertTrue(diagnostics.getClient().isInitialized());
        assertFalse(diagnostics.getClient().isShutdown());
        assertEquals(FeatureState.LOADED, diagnostics.getFeatures().getState());
        assertEquals(FeatureDataSource.LOCAL_REPOSITORY, diagnostics.getFeatures().getSource());
        assertTrue(diagnostics.getFeatures().isAvailable());
        assertEquals(1, diagnostics.getFeatures().getActiveFeatureCount());
        assertEquals(1, diagnostics.getFeatures().getTotalFeatureCount());
        assertTrue(diagnostics.getFeatures().getKeys().isAvailable());
        assertEquals(1, diagnostics.getFeatures().getKeys().getTotalCount());
        assertEquals(20, diagnostics.getFeatures().getKeys().getSampleLimit());
        assertEquals(Collections.singletonList("test-feature"), diagnostics.getFeatures().getKeys().getSample());
        assertFalse(diagnostics.getFeatures().getKeys().isTruncated());
        assertEquals("283ea234b7cfc1f14d1965e98fbef70dec5524ce5cbc38d946846648572501f6",
                diagnostics.getFeatures().getKeys().getSha256());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, diagnostics.getRefresh().getStrategy());
        assertEquals(Long.valueOf(lastFetchMillis), diagnostics.getRefresh().getLastSuccessfulRefreshAtMillis());
        assertEquals(Long.valueOf(generatedAt - lastFetchMillis), diagnostics.getRefresh().getLastSuccessfulRefreshAgeMillis());
        assertEquals(Long.valueOf(expiresAtSeconds * 1000L), diagnostics.getRefresh().getNextCacheExpiresAtMillis());
        assertEquals(Long.valueOf(expiresAtSeconds * 1000L - generatedAt), diagnostics.getRefresh().getMillisUntilCacheExpiry());
        assertFalse(diagnostics.getRefresh().isStale());
        assertEquals(1L, diagnostics.getRefresh().getSuccessCount());
        assertEquals(0L, diagnostics.getRefresh().getFailureCount());
        assertEquals(0L, diagnostics.getRefresh().getConsecutiveFailures());
        assertNull(diagnostics.getRefresh().getLastFailureAtMillis());
        assertEquals(Boolean.FALSE, diagnostics.getRefresh().getLastLoadedFromCache());
        assertNull(diagnostics.getRefresh().getLastError());
        assertTrue(diagnostics.getCache().isEnabled());
        assertEquals(CacheState.AVAILABLE, diagnostics.getCache().getState());
        assertEquals(cacheUpdatedMillis, diagnostics.getCache().getLastUpdatedMillis());
        assertEquals(Long.valueOf(generatedAt - cacheUpdatedMillis), diagnostics.getCache().getAgeMillis());
        assertEquals(StreamingState.DISABLED, diagnostics.getStreaming().getState());
        assertEquals(0, diagnostics.getStreaming().getReconnectAttempts());
    }

    @Test
    @DisplayName("Serializes diagnostics of an initialized client without any key in clear")
    void serializesInitializedDiagnosticsWithoutKeysInClear() {
        // Given
        GrowthBookClient client = initializedClient(createDefaultOptions());

        // When
        String json = client.getDiagnostics().toJson();

        // Then
        assertTrue(json.contains("cust..._key"));
        assertFalse(json.contains("custom_key"));
        assertFalse(json.contains("test_key"));
    }

    @Test
    @DisplayName("Reports degraded health when the feature data has expired")
    void reportsDegradedHealthForExpiredFeatureData() {
        // Given
        long nowMillis = System.currentTimeMillis();
        long expiresAtSeconds = TimeUnit.MILLISECONDS.toSeconds(nowMillis - TimeUnit.SECONDS.toMillis(1));
        when(repository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis - TimeUnit.MINUTES.toMillis(5));
        when(repository.getExpiresAt()).thenReturn(expiresAtSeconds);
        GrowthBookClient client = initializedClient(createDefaultOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertTrue(diagnostics.getRefresh().isStale());
        assertEquals(Long.valueOf(expiresAtSeconds * 1000L - diagnostics.getGeneratedAtMillis()),
                diagnostics.getRefresh().getMillisUntilCacheExpiry());
        assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.FEATURES_STALE), issueCodes(diagnostics));
    }

    @Test
    @DisplayName("Reports degraded health when the features came from an old cache entry")
    void reportsDegradedHealthForOldCacheFallback() {
        // Given
        long nowMillis = System.currentTimeMillis();
        long cacheUpdatedMillis = nowMillis - TimeUnit.MINUTES.toMillis(5);
        when(repository.getLastSuccessfulFetchAtMillis()).thenReturn(0L);
        when(repository.getLastRefreshLoadedFromCache()).thenReturn(true);
        when(repository.getCacheLastUpdatedMillis()).thenReturn(cacheUpdatedMillis);
        when(repository.getExpiresAt()).thenReturn(TimeUnit.MILLISECONDS.toSeconds(nowMillis + TimeUnit.MINUTES.toMillis(5)));
        GrowthBookClient client = initializedClient(createDefaultOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        long expectedExpiry = cacheUpdatedMillis + TimeUnit.SECONDS.toMillis(60);
        assertEquals(Long.valueOf(expectedExpiry), diagnostics.getRefresh().getNextCacheExpiresAtMillis());
        assertEquals(Long.valueOf(expectedExpiry - diagnostics.getGeneratedAtMillis()), diagnostics.getRefresh().getMillisUntilCacheExpiry());
        assertTrue(diagnostics.getRefresh().isStale());
        assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.FEATURES_STALE), issueCodes(diagnostics));
    }

    @Test
    @DisplayName("Keeps a recovered refresh error for inspection without degrading health")
    void keepsRecoveredRefreshErrorWithoutDegradingHealth() {
        // Given
        long nowMillis = System.currentTimeMillis();
        when(repository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis);
        stubRefreshFailure(nowMillis - TimeUnit.SECONDS.toMillis(1), 0L);
        GrowthBookClient client = initializedClient(createDefaultOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals("FeatureFetchException", diagnostics.getRefresh().getLastError().getType());
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR.name(),
                diagnostics.getRefresh().getLastError().getCode());
        assertEquals(DiagnosticsHealthState.READY, diagnostics.getHealth().getState());
        assertEquals(Collections.<DiagnosticsIssueCode>emptyList(), issueCodes(diagnostics));
    }

    @Test
    @DisplayName("Reports degraded health for a refresh failure after an earlier success")
    void reportsDegradedHealthForUnresolvedRefreshFailure() {
        // Given
        long nowMillis = System.currentTimeMillis();
        when(repository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(2));
        stubRefreshFailure(nowMillis - TimeUnit.SECONDS.toMillis(1), 1L);
        GrowthBookClient client = initializedClient(createDefaultOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.REFRESH_FAILED), issueCodes(diagnostics));
    }

    @Test
    @DisplayName("Reports a shut-down client without the feature count it had before")
    void reportsShutDownClientWithoutFeatureCount() {
        // Given
        GrowthBookClient client = initializedClient(createDefaultOptions());
        client.shutdown();

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals(DiagnosticsHealthState.SHUTDOWN, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.CLIENT_SHUTDOWN), issueCodes(diagnostics));
        assertTrue(diagnostics.getClient().isShutdown());
        assertFalse(diagnostics.getFeatures().isAvailable());
        assertEquals(0, diagnostics.getFeatures().getActiveFeatureCount());
    }

    @Test
    @DisplayName("Reports a failed initialization with its error, redacting credentials and keys")
    void reportsFailedInitializationWithRedactedError() throws FeatureFetchException {
        // Given
        GBFeaturesRepository failingRepository = mock(GBFeaturesRepository.class);
        when(failingRepository.getInitialized()).thenReturn(false);
        String unsafeErrorMessage = "network unavailable for "
                + "https://user:pass@custom.growthbook.io/api/features/custom_key?token=secret using test_key";
        doThrow(new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, unsafeErrorMessage))
                .when(failingRepository).initialize(anyBoolean());
        when(repositoryBuilder.build()).thenReturn(failingRepository);
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions());
        boolean initialized = client.initialize();

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertFalse(initialized);
        assertFalse(diagnostics.getClient().isInitialized());
        assertEquals(DiagnosticsHealthState.ERROR, diagnostics.getHealth().getState());
        assertTrue(issueCodes(diagnostics).contains(DiagnosticsIssueCode.REFRESH_FAILED));
        assertEquals("FeatureFetchException", diagnostics.getRefresh().getLastError().getType());
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR.name(),
                diagnostics.getRefresh().getLastError().getCode());
        String message = diagnostics.getRefresh().getLastError().getMessage();
        assertTrue(message.contains("network unavailable"));
        assertTrue(message.contains("cust..._key"));
        assertTrue(diagnostics.getRefresh().getLastError().getTimestampMillis() > 0);
        assertNoSecrets(message);
        assertNoSecrets(diagnostics.toJson());
    }

    @Test
    @DisplayName("Masks a short client key entirely")
    void masksShortClientKeyEntirely() {
        // Given
        Options options = Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("abc")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals("***", diagnostics.getConfig().getClientKeyMasked());
        assertEquals("https://custom.growthbook.io/api/features/***", diagnostics.getConfig().getFeaturesEndpoint());
        assertEquals("https://custom.growthbook.io/sub/***", diagnostics.getConfig().getEventsEndpoint());
        assertFalse(diagnostics.toJson().contains("/abc"));
    }

    @Test
    @DisplayName("Redacts URL user info, query parameters and keys from the reported configuration")
    void redactsUrlUserInfoQueryAndKeys() {
        // Given
        Options options = Options.builder()
                .apiHost("https://user:pass@custom.growthbook.io?token=secret")
                .clientKey("custom_key")
                .decryptionKey("super-secret")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        String apiHost = diagnostics.getConfig().getApiHost();
        assertFalse(apiHost.contains("user:pass"));
        assertFalse(apiHost.contains("token=secret"));
        String json = diagnostics.toJson();
        assertFalse(json.contains("user:pass"));
        assertFalse(json.contains("token=secret"));
        assertFalse(json.contains("custom_key"));
        assertFalse(json.contains("super-secret"));
    }

    @Test
    @DisplayName("Reading diagnostics before initialization does not create a repository")
    void readingDiagnosticsBeforeInitializationDoesNotCreateRepository() {
        // Given
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions());

        // When
        client.getDiagnostics();

        // Then
        repositoryStatics.verify(GBFeaturesRepository::builder, never());
    }

    @Test
    @DisplayName("Reports a connected stream as ready")
    void reportsConnectedStreamAsReady() {
        // Given
        stubStreaming(true, true, 0);
        GrowthBookClient client = initializedClient(sseOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals(StreamingState.CONNECTED, diagnostics.getStreaming().getState());
        assertEquals(DiagnosticsHealthState.READY, diagnostics.getHealth().getState());
    }

    @Test
    @DisplayName("Reports unsupported streaming with degraded health when the server does not allow SSE")
    void reportsUnsupportedStreamingWhenServerDisallowsSse() {
        // Given
        stubStreaming(false, false, 0);
        GrowthBookClient client = initializedClient(sseOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals(StreamingState.UNSUPPORTED, diagnostics.getStreaming().getState());
        assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.STREAMING_UNSUPPORTED), issueCodes(diagnostics));
    }

    @Test
    @DisplayName("Reports an interrupted stream with its reconnect attempts while reconnecting")
    void reportsInterruptedStreamWhileReconnecting() {
        // Given
        stubStreaming(true, false, 3);
        GrowthBookClient client = initializedClient(sseOptions());

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertEquals(StreamingState.INTERRUPTED, diagnostics.getStreaming().getState());
        assertEquals(3, diagnostics.getStreaming().getReconnectAttempts());
        assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
        assertEquals(Collections.singletonList(DiagnosticsIssueCode.STREAMING_INTERRUPTED), issueCodes(diagnostics));
    }

    @Test
    @DisplayName("Reports the remote-eval configuration before initialization")
    void reportsRemoteEvalConfigurationBeforeInitialization() {
        // Given
        Options options = Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .remoteEval(true)
                .remoteEvalCacheSize(42)
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        Diagnostics diagnostics = client.getDiagnostics();

        // Then
        assertTrue(diagnostics.getClient().isRemoteEvalEnabled());
        assertFalse(diagnostics.getClient().isInitialized());
        assertEquals(DiagnosticsHealthState.NOT_READY, diagnostics.getHealth().getState());
        assertTrue(diagnostics.getRemoteEval().isEnabled());
        assertFalse(diagnostics.getRemoteEval().isReady());
        assertFalse(diagnostics.getRemoteEval().isCacheConfigured());
        assertEquals(42, diagnostics.getRemoteEval().getCacheMaxSize());
        assertEquals(StreamingState.DISABLED, diagnostics.getStreaming().getState());
        assertEquals(FeatureDataSource.REMOTE_EVAL_FALLBACK, diagnostics.getFeatures().getSource());
        assertEquals(FeatureState.NOT_LOADED, diagnostics.getFeatures().getState());
        assertEquals(0, diagnostics.getFeatures().getActiveFeatureCount());
        assertFalse(diagnostics.getFeatures().getKeys().isAvailable());
    }

    private GrowthBookClient initializedClient(Options options) {
        GrowthBookClient client = new GrowthBookClient(options);
        assertTrue(client.initialize());
        return client;
    }

    private void stubRefreshFailure(long failedAtMillis, long consecutiveFailures) {
        when(repository.getRefreshFailureCount()).thenReturn(1L);
        when(repository.getRefreshConsecutiveFailureCount()).thenReturn(consecutiveFailures);
        when(repository.getLastRefreshFailureAtMillis()).thenReturn(failedAtMillis);
        when(repository.getLastRefreshErrorAtMillis()).thenReturn(failedAtMillis);
        when(repository.getLastRefreshError()).thenReturn(new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, FETCH_ERROR_MESSAGE));
    }

    private void stubStreaming(boolean sseAllowed, boolean sseConnected, int retryAttempts) {
        when(repository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        when(repository.isSseAllowed()).thenReturn(sseAllowed);
        when(repository.isSseConnected()).thenReturn(sseConnected);
        when(repository.getSseRetryAttempts()).thenReturn(retryAttempts);
    }

    private static Options sseOptions() {
        return Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .decryptionKey("test_key")
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build();
    }

    private static List<DiagnosticsIssueCode> issueCodes(Diagnostics diagnostics) {
        return new ArrayList<>(diagnostics.getHealth().getIssues().stream()
                .map(issue -> issue.getCode())
                .collect(Collectors.toList()));
    }

    private static void assertNoSecrets(String text) {
        assertFalse(text.contains("custom_key"));
        assertFalse(text.contains("user:pass"));
        assertFalse(text.contains("token=secret"));
        assertFalse(text.contains("test_key"));
    }
}
