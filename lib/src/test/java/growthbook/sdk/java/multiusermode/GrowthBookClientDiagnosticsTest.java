package growthbook.sdk.java.multiusermode;

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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createDefaultOptions;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockBuilder;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class GrowthBookClientDiagnosticsTest {

    private GBFeaturesRepository mockRepository;
    private GBFeaturesRepository.GBFeaturesRepositoryBuilder mockBuilder;

    @Test
    void test_getDiagnostics_beforeInitializeReportsDefaultState() {
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));

        Diagnostics diagnostics = client.getDiagnostics();

        assertNotNull(diagnostics);
        assertTrue(diagnostics.getGeneratedAtMillis() > 0);
        assertEquals(Version.SDK_VERSION, diagnostics.getSdk().getVersion());
        assertEquals(DiagnosticsHealthState.NOT_READY, diagnostics.getHealth().getState());
        assertEquals(1, diagnostics.getHealth().getIssues().size());
        assertEquals(
                DiagnosticsIssueCode.CLIENT_NOT_INITIALIZED,
                diagnostics.getHealth().getIssues().get(0).getCode()
        );
        assertEquals("https://custom.growthbook.io", diagnostics.getConfig().getApiHost());
        assertEquals("cust..._key", diagnostics.getConfig().getClientKeyMasked());
        assertEquals(
                "https://custom.growthbook.io/api/features/cust..._key",
                diagnostics.getConfig().getFeaturesEndpoint()
        );
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

        String diagnosticsJson = diagnostics.toJson();
        assertTrue(diagnosticsJson.contains("\"config\""));
        assertTrue(diagnosticsJson.contains("cust..._key"));
        assertFalse(diagnosticsJson.contains("custom_key"));
        assertFalse(diagnosticsJson.contains("test_key"));
    }

    @Test
    void test_getDiagnostics_afterInitializeReportsRepositoryState() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(DiagnosticsHealthState.READY, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().isEmpty());
            assertEquals("https://custom.growthbook.io", diagnostics.getConfig().getApiHost());
            assertEquals("cust..._key", diagnostics.getConfig().getClientKeyMasked());
            assertEquals(
                    "https://custom.growthbook.io/api/features/cust..._key",
                    diagnostics.getConfig().getFeaturesEndpoint()
            );
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
            assertEquals(
                    "283ea234b7cfc1f14d1965e98fbef70dec5524ce5cbc38d946846648572501f6",
                    diagnostics.getFeatures().getKeys().getSha256()
            );
            assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, diagnostics.getRefresh().getStrategy());
            assertNotNull(diagnostics.getRefresh().getLastSuccessfulRefreshAtMillis());
            assertNotNull(diagnostics.getRefresh().getLastSuccessfulRefreshAgeMillis());
            assertTrue(diagnostics.getRefresh().getLastSuccessfulRefreshAgeMillis() >= 0);
            assertTrue(diagnostics.getRefresh().getLastSuccessfulRefreshAgeMillis() < TimeUnit.SECONDS.toMillis(10));
            assertNotNull(diagnostics.getRefresh().getNextCacheExpiresAtMillis());
            assertNotNull(diagnostics.getRefresh().getMillisUntilCacheExpiry());
            assertTrue(diagnostics.getRefresh().getMillisUntilCacheExpiry() > 0);
            assertFalse(diagnostics.getRefresh().isStale());
            assertEquals(1L, diagnostics.getRefresh().getSuccessCount());
            assertEquals(0L, diagnostics.getRefresh().getFailureCount());
            assertEquals(0L, diagnostics.getRefresh().getConsecutiveFailures());
            assertNull(diagnostics.getRefresh().getLastFailureAtMillis());
            assertEquals(Boolean.FALSE, diagnostics.getRefresh().getLastLoadedFromCache());
            assertNull(diagnostics.getRefresh().getLastError());
            assertTrue(diagnostics.getCache().isEnabled());
            assertEquals(CacheState.AVAILABLE, diagnostics.getCache().getState());
            assertTrue(diagnostics.getCache().getLastUpdatedMillis() > 0);
            assertNotNull(diagnostics.getCache().getAgeMillis());
            assertTrue(diagnostics.getCache().getAgeMillis() >= 0);
            assertTrue(diagnostics.getCache().getAgeMillis() < TimeUnit.SECONDS.toMillis(10));
            assertEquals(StreamingState.DISABLED, diagnostics.getStreaming().getState());
            assertEquals(0, diagnostics.getStreaming().getReconnectAttempts());

            String diagnosticsJson = diagnostics.toJson();
            assertTrue(diagnosticsJson.contains("cust..._key"));
            assertFalse(diagnosticsJson.contains("custom_key"));
            assertFalse(diagnosticsJson.contains("test_key"));
        }
    }

    @Test
    void test_getDiagnostics_reportsStaleFeatureData() {
        mockRepository = createMockRepository();
        long nowMillis = System.currentTimeMillis();
        when(mockRepository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis - TimeUnit.MINUTES.toMillis(5));
        when(mockRepository.getExpiresAt()).thenReturn(new AtomicLong(TimeUnit.MILLISECONDS.toSeconds(nowMillis - TimeUnit.SECONDS.toMillis(1))));
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertTrue(diagnostics.getRefresh().isStale());
            assertNotNull(diagnostics.getRefresh().getMillisUntilCacheExpiry());
            assertTrue(diagnostics.getRefresh().getMillisUntilCacheExpiry() < 0);
            assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.FEATURES_STALE));
        }
    }

    @Test
    void test_getDiagnostics_reportsStaleFeatureDataWhenOldCacheFallbackWasLoaded() {
        mockRepository = createMockRepository();
        long nowMillis = System.currentTimeMillis();
        long cacheUpdatedMillis = nowMillis - TimeUnit.MINUTES.toMillis(5);
        when(mockRepository.getLastSuccessfulFetchAtMillis()).thenReturn(0L);
        when(mockRepository.getLastRefreshLoadedFromCache()).thenReturn(true);
        when(mockRepository.getCacheLastUpdatedMillis()).thenReturn(cacheUpdatedMillis);
        when(mockRepository.getExpiresAt()).thenReturn(new AtomicLong(TimeUnit.MILLISECONDS.toSeconds(nowMillis + TimeUnit.MINUTES.toMillis(5))));
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(cacheUpdatedMillis + TimeUnit.SECONDS.toMillis(60), diagnostics.getRefresh().getNextCacheExpiresAtMillis());
            assertTrue(diagnostics.getRefresh().isStale());
            assertTrue(diagnostics.getRefresh().getMillisUntilCacheExpiry() < 0);
            assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.FEATURES_STALE));
        }
    }

    @Test
    void test_getDiagnostics_keepsHistoricalErrorWithoutDegradingHealthAfterRecovery() {
        mockRepository = createMockRepository();
        long nowMillis = System.currentTimeMillis();
        when(mockRepository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis);
        when(mockRepository.getRefreshFailureCount()).thenReturn(1L);
        when(mockRepository.getRefreshConsecutiveFailureCount()).thenReturn(0L);
        when(mockRepository.getLastRefreshFailureAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(1));
        when(mockRepository.getLastRefreshErrorAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(1));
        when(mockRepository.getLastRefreshError()).thenReturn(new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                "temporary outage for custom_key"
        ));
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertNotNull(diagnostics.getRefresh().getLastError());
            assertEquals(DiagnosticsHealthState.READY, diagnostics.getHealth().getState());
            assertFalse(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.REFRESH_FAILED));
        }
    }

    @Test
    void test_getDiagnostics_reportsUnresolvedRefreshFailureAfterPriorSuccess() {
        mockRepository = createMockRepository();
        long nowMillis = System.currentTimeMillis();
        when(mockRepository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(2));
        when(mockRepository.getRefreshFailureCount()).thenReturn(1L);
        when(mockRepository.getRefreshConsecutiveFailureCount()).thenReturn(1L);
        when(mockRepository.getLastRefreshFailureAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(1));
        when(mockRepository.getLastRefreshErrorAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(1));
        when(mockRepository.getLastRefreshError()).thenReturn(new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                "temporary outage for custom_key"
        ));
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.REFRESH_FAILED));
        }
    }

    @Test
    void test_getDiagnostics_afterShutdownDoesNotReportStaleFeatureCount() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertTrue(client.initialize());

            client.shutdown();
            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(DiagnosticsHealthState.SHUTDOWN, diagnostics.getHealth().getState());
            assertEquals(1, diagnostics.getHealth().getIssues().size());
            assertEquals(
                    DiagnosticsIssueCode.CLIENT_SHUTDOWN,
                    diagnostics.getHealth().getIssues().get(0).getCode()
            );
            assertTrue(diagnostics.getClient().isShutdown());
            assertFalse(diagnostics.getFeatures().isAvailable());
            assertEquals(0, diagnostics.getFeatures().getActiveFeatureCount());
        }
    }

    @Test
    void test_getDiagnostics_reportsFailedInitializationError() throws FeatureFetchException {
        mockRepository = mock(GBFeaturesRepository.class);
        when(mockRepository.getInitialized()).thenReturn(false);
        String unsafeErrorMessage = "network unavailable for "
                + "https://user:pass@custom.growthbook.io/api/features/custom_key?token=secret using test_key";
        doThrow(new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, unsafeErrorMessage))
                .when(mockRepository).initialize();
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            assertFalse(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertFalse(diagnostics.getClient().isInitialized());
            assertEquals(DiagnosticsHealthState.ERROR, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.REFRESH_FAILED));
            assertNotNull(diagnostics.getRefresh().getLastError());
            assertEquals("FeatureFetchException", diagnostics.getRefresh().getLastError().getType());
            assertEquals(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR.name(),
                    diagnostics.getRefresh().getLastError().getCode()
            );
            assertTrue(diagnostics.getRefresh().getLastError().getMessage().contains("network unavailable"));
            assertTrue(diagnostics.getRefresh().getLastError().getMessage().contains("cust..._key"));
            assertFalse(diagnostics.getRefresh().getLastError().getMessage().contains("custom_key"));
            assertFalse(diagnostics.getRefresh().getLastError().getMessage().contains("user:pass"));
            assertFalse(diagnostics.getRefresh().getLastError().getMessage().contains("token=secret"));
            assertFalse(diagnostics.getRefresh().getLastError().getMessage().contains("test_key"));
            assertTrue(diagnostics.getRefresh().getLastError().getTimestampMillis() > 0);

            String diagnosticsJson = diagnostics.toJson();
            assertTrue(diagnosticsJson.contains("cust..._key"));
            assertFalse(diagnosticsJson.contains("custom_key"));
            assertFalse(diagnosticsJson.contains("user:pass"));
            assertFalse(diagnosticsJson.contains("token=secret"));
            assertFalse(diagnosticsJson.contains("test_key"));
        }
    }

    @Test
    void test_getDiagnostics_masksShortClientKeys() {
        Options options = Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("abc")
                .build();

        GrowthBookClient client = new GrowthBookClient(options);
        Diagnostics diagnostics = client.getDiagnostics();

        assertEquals("***", diagnostics.getConfig().getClientKeyMasked());
        assertEquals("https://custom.growthbook.io/api/features/***", diagnostics.getConfig().getFeaturesEndpoint());
        assertEquals("https://custom.growthbook.io/sub/***", diagnostics.getConfig().getEventsEndpoint());
        assertFalse(diagnostics.toJson().contains("/abc"));
    }

    @Test
    void test_getDiagnostics_redactsUrlUserInfoAndQueryParams() {
        Options options = Options.builder()
                .apiHost("https://user:pass@custom.growthbook.io?token=secret")
                .clientKey("custom_key")
                .decryptionKey("super-secret")
                .build();

        GrowthBookClient client = new GrowthBookClient(options);
        Diagnostics diagnostics = client.getDiagnostics();
        String diagnosticsJson = diagnostics.toJson();

        assertFalse(diagnostics.getConfig().getApiHost().contains("user:pass"));
        assertFalse(diagnostics.getConfig().getApiHost().contains("token=secret"));
        assertFalse(diagnosticsJson.contains("user:pass"));
        assertFalse(diagnosticsJson.contains("token=secret"));
        assertFalse(diagnosticsJson.contains("custom_key"));
        assertFalse(diagnosticsJson.contains("super-secret"));
    }

    @Test
    void test_getDiagnostics_beforeInitializeDoesNotCreateRepository() {
        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));

            client.getDiagnostics();

            mockedStatic.verify(GBFeaturesRepository::builder, never());
        }
    }

    @Test
    void test_getDiagnostics_reportsDerivedStreamingState() {
        mockRepository = createMockRepository();
        when(mockRepository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        when(mockRepository.isSseAllowed()).thenReturn(true);
        when(mockRepository.isSseConnected()).thenReturn(true);
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = Options.builder()
                    .apiHost("https://custom.growthbook.io")
                    .clientKey("custom_key")
                    .decryptionKey("test_key")
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                        .build();

            GrowthBookClient client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(StreamingState.CONNECTED, diagnostics.getStreaming().getState());
            assertEquals(DiagnosticsHealthState.READY, diagnostics.getHealth().getState());
        }
    }

    @Test
    void test_getDiagnostics_reportsUnsupportedStreamingStateWhenServerDoesNotAllowSse() {
        mockRepository = createMockRepository();
        when(mockRepository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        when(mockRepository.isSseAllowed()).thenReturn(false);
        when(mockRepository.isSseConnected()).thenReturn(false);
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = Options.builder()
                    .apiHost("https://custom.growthbook.io")
                    .clientKey("custom_key")
                    .decryptionKey("test_key")
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                        .build();

            GrowthBookClient client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(StreamingState.UNSUPPORTED, diagnostics.getStreaming().getState());
            assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.STREAMING_UNSUPPORTED));
        }
    }

    @Test
    void test_getDiagnostics_reportsInterruptedStreamingStateWhileReconnecting() {
        mockRepository = createMockRepository();
        when(mockRepository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.SERVER_SENT_EVENTS);
        when(mockRepository.isSseAllowed()).thenReturn(true);
        when(mockRepository.isSseConnected()).thenReturn(false);
        when(mockRepository.getSseRetryAttempts()).thenReturn(3);
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = Options.builder()
                    .apiHost("https://custom.growthbook.io")
                    .clientKey("custom_key")
                    .decryptionKey("test_key")
                    .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                        .build();

            GrowthBookClient client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            Diagnostics diagnostics = client.getDiagnostics();

            assertEquals(StreamingState.INTERRUPTED, diagnostics.getStreaming().getState());
            assertEquals(3, diagnostics.getStreaming().getReconnectAttempts());
            assertEquals(DiagnosticsHealthState.DEGRADED, diagnostics.getHealth().getState());
            assertTrue(diagnostics.getHealth().getIssues().stream()
                    .anyMatch(issue -> issue.getCode() == DiagnosticsIssueCode.STREAMING_INTERRUPTED));
        }
    }

    @Test
    void test_getDiagnostics_remoteEvalBeforeInitializeReportsRemoteEvalConfiguration() {
        Options options = Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .remoteEval(true)
                .remoteEvalCacheSize(42)
                .build();

        GrowthBookClient client = new GrowthBookClient(options);
        Diagnostics diagnostics = client.getDiagnostics();

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
}
