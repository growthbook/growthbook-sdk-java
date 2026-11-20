package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.testhelpers.BlockingResponseTransformer;
import growthbook.sdk.java.testhelpers.FixedPayloadCacheManager;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GBFeaturesRepositoryRefreshTest {

    private static final String CLIENT_KEY = "sdk-abc123";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final String FEATURES_RESPONSE = "{\"features\":{\"test\":{\"defaultValue\":true}}}";
    private static final String FETCHED_FEATURES = "{\"test\":{\"defaultValue\":true}}";
    private static final String CACHED_RESPONSE = "{\"features\":{\"cached\":{\"defaultValue\":true}}}";
    private static final String CACHED_FEATURES = "{\"cached\":{\"defaultValue\":true}}";
    private static final long FRESH_CACHE_UPDATED_AT_MILLIS = Instant.parse("2999-01-01T00:00:00Z").toEpochMilli();
    private static final long STALE_CACHE_UPDATED_AT_MILLIS = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

    private final List<GBFeaturesRepository> repositories = new ArrayList<>();
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        for (GBFeaturesRepository repository : repositories) {
            repository.shutdown();
        }
        wireMock.stop();
    }

    @Test
    @DisplayName("A forced refresh bypasses a fresh cache and asks the server for an uncached response")
    void forcedRefreshBypassesFreshCache() throws Exception {
        // Given
        wireMock.stubFor(okFeatures());
        GBFeaturesRepository repository = track(repositoryBuilder()
                .cacheManager(new FixedPayloadCacheManager(CACHED_RESPONSE, FRESH_CACHE_UPDATED_AT_MILLIS))
                .backgroundFetchInterval(Duration.ofHours(48))
                .build());
        repository.initialize();

        // When
        repository.refreshFeatures(RefreshMode.FORCE);

        // Then
        assertEquals(FETCHED_FEATURES, repository.getFeaturesJson());
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH))
                .withHeader("Cache-Control", equalTo("no-cache"))
                .withoutHeader("If-None-Match"));
    }

    @Test
    @DisplayName("Retries after a network failure and applies the next successful response")
    void retriesAfterNetworkFailure() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody(FEATURES_RESPONSE)));
        GBFeaturesRepository repository = track(repositoryBuilder().isCacheDisabled(true).build());

        // When
        repository.fetchFeatures();

        // Then
        assertEquals(FETCHED_FEATURES, repository.getFeaturesJson());
        wireMock.verify(2, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("Falls back to the cache once every retry has failed, and stops retrying")
    void fallsBackToCacheAfterRetriesExhausted() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        FixedPayloadCacheManager cacheManager = new FixedPayloadCacheManager(CACHED_RESPONSE);
        GBFeaturesRepository repository = track(repositoryBuilder().cacheManager(cacheManager).build());

        // When
        repository.fetchFeatures();

        // Then
        assertEquals(CACHED_FEATURES, repository.getFeaturesJson());
        wireMock.verify(5, getRequestedFor(urlEqualTo(FEATURES_PATH)));
        assertEquals(1, cacheManager.loadCount());
    }

    @Test
    @DisplayName("A failed refresh keeps the held features instead of loading an older cache")
    void failedRefreshKeepsHeldFeatures() throws Exception {
        // Given
        wireMock.stubFor(okFeatures());
        FixedPayloadCacheManager cacheManager = new FixedPayloadCacheManager(CACHED_RESPONSE);
        GBFeaturesRepository repository = track(repositoryBuilder().cacheManager(cacheManager).build());
        repository.initialize();
        wireMock.resetMappings();
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // When
        repository.refreshFeatures(RefreshMode.FORCE);

        // Then
        assertEquals(FETCHED_FEATURES, repository.getFeaturesJson());
        wireMock.verify(6, getRequestedFor(urlEqualTo(FEATURES_PATH)));
        assertEquals(0, cacheManager.loadCount());
    }

    @Test
    @DisplayName("A requested background refresh returns before the fetch completes and then applies the features")
    void requestedBackgroundRefreshDoesNotBlockCaller() throws Exception {
        // Given
        BlockingResponseTransformer blocking = new BlockingResponseTransformer();
        WireMockServer blockingServer = new WireMockServer(WireMockConfiguration.options().dynamicPort().extensions(blocking));
        blockingServer.start();
        try {
            blockingServer.stubFor(okFeatures());
            GBFeaturesRepository repository = track(GBFeaturesRepository.builder()
                    .apiHost("http://localhost:" + blockingServer.port())
                    .clientKey(CLIENT_KEY)
                    .isCacheDisabled(true)
                    .retryPolicy(NO_DELAY_RETRY_POLICY)
                    .build());
            LatchedRefreshListener listener = new LatchedRefreshListener();
            repository.addFeatureRefreshListener(listener);

            // When
            repository.requestFeatureRefresh(RefreshMode.FORCE);

            // Then
            assertTrue(blocking.awaitRequest(5, TimeUnit.SECONDS));
            assertEquals("{}", repository.getFeaturesJson());
            assertEquals(1, listener.refreshed.getCount());
            blocking.release();
            assertTrue(listener.refreshed.await(5, TimeUnit.SECONDS));
            assertEquals(FETCHED_FEATURES, repository.getFeaturesJson());
        } finally {
            blocking.release();
            blockingServer.stop();
        }
    }

    @Test
    @DisplayName("A fresh cache within the background fetch interval skips the network on initialize")
    void freshCacheSkipsNetworkOnInitialize() throws Exception {
        // Given
        wireMock.stubFor(okFeatures());
        GBFeaturesRepository repository = track(repositoryBuilder()
                .cacheManager(new FixedPayloadCacheManager(CACHED_RESPONSE, FRESH_CACHE_UPDATED_AT_MILLIS))
                .backgroundFetchInterval(Duration.ofHours(48))
                .build());

        // When
        repository.initialize();

        // Then
        assertEquals(CACHED_FEATURES, repository.getFeaturesJson());
        wireMock.verify(0, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("A cache older than the background fetch interval is refreshed from the network on initialize")
    void staleCacheRefreshesFromNetworkOnInitialize() throws Exception {
        // Given
        wireMock.stubFor(okFeatures());
        GBFeaturesRepository repository = track(repositoryBuilder()
                .cacheManager(new FixedPayloadCacheManager(CACHED_RESPONSE, STALE_CACHE_UPDATED_AT_MILLIS))
                .backgroundFetchInterval(Duration.ofHours(1))
                .build());

        // When
        repository.initialize();

        // Then
        assertEquals(FETCHED_FEATURES, repository.getFeaturesJson());
        wireMock.verify(1, getRequestedFor(urlEqualTo(FEATURES_PATH)));
    }

    @Test
    @DisplayName("Builds without a background fetch interval and with the default retry policy")
    void buildsWithoutBackgroundFetchIntervalAndWithDefaultRetryPolicy() {
        // Given
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = GBFeaturesRepository.builder().clientKey(CLIENT_KEY);

        // When
        GBFeaturesRepository repository = builder.build();

        // Then
        assertNull(repository.getBackgroundFetchInterval());
        assertEquals(FeatureFetchRetryPolicy.DEFAULT_MAX_ATTEMPTS, repository.getRetryPolicy().getMaxAttempts());
    }

    @Test
    @DisplayName("Rejects a negative background fetch interval")
    void rejectsNegativeBackgroundFetchInterval() {
        // Given
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = GBFeaturesRepository.builder()
                .clientKey(CLIENT_KEY)
                .backgroundFetchInterval(Duration.ofSeconds(-1));

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);

        // Then
        assertEquals("backgroundFetchInterval must not be negative", exception.getMessage());
    }

    private GBFeaturesRepository.GBFeaturesRepositoryBuilder repositoryBuilder() {
        return GBFeaturesRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey(CLIENT_KEY)
                .okHttpClient(new OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private GBFeaturesRepository track(GBFeaturesRepository repository) {
        repositories.add(repository);
        return repository;
    }

    private static MappingBuilder okFeatures() {
        return get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("ETag", "v1")
                .withBody(FEATURES_RESPONSE));
    }

    private static final class LatchedRefreshListener implements FeatureRefreshListener {
        private final CountDownLatch refreshed = new CountDownLatch(1);

        @Override
        public void onRefresh(FeatureRefreshEvent event) {
            if (event.isSuccessful()) {
                refreshed.countDown();
            }
        }
    }
}
