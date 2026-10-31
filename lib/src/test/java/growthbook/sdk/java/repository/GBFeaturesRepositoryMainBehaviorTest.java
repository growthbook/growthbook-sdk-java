package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.repository.internal.FeatureEndpoints;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.InMemoryCachingManagerImpl;
import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How {@link GBFeaturesRepository} uses its feature cache.
 */
class GBFeaturesRepositoryMainBehaviorTest {

    private static final String CLIENT_KEY = "sdk-abc123";
    private static final String FEATURES_PATH = "/api/features/" + CLIENT_KEY;
    private static final String LEGACY_CACHE_KEY = "FEATURE_CACHE.json";
    private static final String FEATURES_RESPONSE = "{\"features\":{\"flag\":{\"defaultValue\":true}}}";
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
    @DisplayName("Shutdown releases the cache manager without clearing the durable cache")
    void shutdownKeepsDurableCache() throws Exception {
        // Given
        wireMock.stubFor(get(urlEqualTo(FEATURES_PATH)).willReturn(aResponse().withStatus(200).withBody(FEATURES_RESPONSE)));
        InMemoryCacheManager cacheManager = new InMemoryCacheManager();
        GBFeaturesRepository subject = track(repositoryBuilder()
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .cacheManager(cacheManager)
                .build());
        subject.initialize();

        // When
        subject.shutdown();

        // Then
        assertFalse(cacheManager.cleared());
        assertEquals(FEATURES_RESPONSE, cacheManager.store.get(cacheKey()));
        assertNull(subject.getCacheManager());
    }

    @Test
    @DisplayName("The cache timestamp is read from the endpoint's own key, not the legacy key")
    void cacheTimestampIgnoresLegacyKey() {
        // Given
        GbCacheManager cacheManager = new LegacyTimestampCacheManager(123_456L);

        // When
        GBFeaturesRepository subject = track(GBFeaturesRepository.builder()
                .apiHost(apiHost())
                .clientKey(CLIENT_KEY)
                .cacheManager(cacheManager)
                .build());

        // Then
        assertNull(subject.getCacheLastUpdatedMillis());
    }

    @Test
    @DisplayName("Has no cache manager when caching is disabled")
    void hasNoCacheManagerWhenCachingDisabled() {
        // Given
        GbCacheManager provided = new InMemoryCacheManager();

        // When
        GBFeaturesRepository subject = new GBFeaturesRepository("", "", null, null, null, null, true, null, provided);

        // Then
        assertNull(subject.getCacheManager());
        assertTrue(subject.isCacheDisabled());
    }

    @Test
    @DisplayName("Uses the provided cache manager")
    void usesProvidedCacheManager() {
        // Given
        GbCacheManager provided = new InMemoryCacheManager();

        // When
        GBFeaturesRepository subject = new GBFeaturesRepository("", "", null, null, null, null, false, null, provided);

        // Then
        assertSame(provided, subject.getCacheManager());
        assertFalse(subject.isCacheDisabled());
    }

    @Test
    @DisplayName("Creates a file or in-memory cache manager when none is provided")
    void createsDefaultCacheManagerWhenNoneProvided() {
        // Given
        GbCacheManager provided = null;

        // When
        GBFeaturesRepository subject = new GBFeaturesRepository("", "", null, null, null, null, false, null, provided);

        // Then
        GbCacheManager created = subject.getCacheManager();
        assertTrue(created instanceof FileCachingManagerImpl || created instanceof InMemoryCachingManagerImpl,
                "unexpected default cache manager: " + created);
    }

    private GBFeaturesRepository.GBFeaturesRepositoryBuilder repositoryBuilder() {
        return GBFeaturesRepository.builder()
                .apiHost(apiHost())
                .clientKey(CLIENT_KEY)
                .okHttpClient(new OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .retryPolicy(NO_DELAY_RETRY_POLICY);
    }

    private String apiHost() {
        return "http://localhost:" + wireMock.port();
    }

    private String cacheKey() {
        return FeatureEndpoints.of(apiHost(), CLIENT_KEY).cacheKey();
    }

    private GBFeaturesRepository track(GBFeaturesRepository repository) {
        repositories.add(repository);
        return repository;
    }

    private static final class LegacyTimestampCacheManager implements GbCacheManager {
        private final long legacyTimestampMillis;

        private LegacyTimestampCacheManager(long legacyTimestampMillis) {
            this.legacyTimestampMillis = legacyTimestampMillis;
        }

        @Override
        public Long getLastUpdatedMillis(String key) {
            return LEGACY_CACHE_KEY.equals(key) ? legacyTimestampMillis : null;
        }

        @Override
        public void saveContent(String key, String data) {
        }

        @Override
        public String loadCache(String key) {
            return null;
        }

        @Override
        public void clearCache() {
        }
    }
}
