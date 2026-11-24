package growthbook.sdk.java.multiusermode;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client-facing behaviour of the inline bootstrap payload: an offline cold start that still evaluates
 * from the seeded snapshot, and a fail-fast on a malformed payload. The "unreachable API" is modelled
 * with a WireMock server that always errors, so no real network is involved.
 */
class GrowthBookClientInitialPayloadTest {

    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        // Stand-in for an unreachable API: the features endpoint always errors, so the SDK must fall
        // back to the seeded payload for evaluation.
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse().withStatus(500)));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @DisplayName("Offline: initialize() returns true and isOn() evaluates from the seeded payload")
    void offlineClient_evaluatesFromInitialPayload() {
        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        boolean initialized = client.initialize();

        assertTrue(initialized, "client should be ready from the seeded payload even while offline");
        assertTrue(client.isOn("my-flag", UserContext.builder().build()));

        client.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("Offline SSE: a seeded start does not block initialize() on the network and serves the seed")
    void seededSseClient_initializesWithoutBlocking() {
        // Given
        GatedResponseTransformer gate = new GatedResponseTransformer();
        WireMockServer gatedServer = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .extensions(gate));
        gatedServer.start();
        gatedServer.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")));
        Options options = Options.builder()
                .apiHost("http://localhost:" + gatedServer.port())
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        try {
            // When
            boolean initialized = client.initialize();

            // Then
            assertTrue(initialized, "seeded SSE client should be ready from the seed without waiting on the network");
            assertTrue(client.isOn("my-flag", UserContext.builder().build()));
        } finally {
            gate.release.countDown();
            client.shutdown();
            gatedServer.stop();
        }
    }

    @Test
    @DisplayName("Malformed payload: initialize() reports failure instead of silently starting empty")
    void malformedPayload_reportsStartupFailure() {
        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{ not valid json")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        assertFalse(client.initialize());
        client.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("After a failed seeded start, a refresh that loads a fresh cached payload updates evaluation")
    void failedSeededStart_freshCacheLoadedOnRefresh_reachesEvaluation() throws Exception {
        // Given: the API is down, the seed turns the flag on, and a fresh shared cache turns it off.
        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey("sdk-abc123")
                .cacheManager(new FreshCacheManager("{\"features\":{\"my-flag\":{\"defaultValue\":false}}}"))
                .backgroundFetchInterval(Duration.ofHours(1))
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        CountDownLatch initialFetchFailed = new CountDownLatch(1);
        CountDownLatch cacheLoaded = new CountDownLatch(1);
        client.addFeatureRefreshListener(event -> {
            if (event.getSource() == FeatureRefreshSource.INITIALIZATION && !event.isSuccessful()) {
                initialFetchFailed.countDown();
            }
            if (event.getSource() == FeatureRefreshSource.MANUAL && event.isSuccessful()
                    && event.isFeaturesChanged() && event.isLoadedFromCache()) {
                cacheLoaded.countDown();
            }
        });

        try {
            assertTrue(client.initialize());
            assertTrue(initialFetchFailed.await(5, TimeUnit.SECONDS), "seeded background fetch did not fail");
            assertTrue(client.isOn("my-flag", UserContext.builder().build()), "seed should serve first");

            // When: the cache is fresh, so the refresh skips the network and loads the cached payload.
            client.refreshFeatures(RefreshMode.DEFAULT);

            // Then: listeners hear about it and evaluation no longer serves the seed.
            assertTrue(cacheLoaded.await(5, TimeUnit.SECONDS), "cache load was not published to listeners");
            assertFalse(client.isOn("my-flag", UserContext.builder().build()),
                    "evaluation must use the cached payload, not the stale seed");
        } finally {
            client.shutdown();
        }
    }

    /**
     * Shared cache that holds a payload and always reports it as just written, so a default refresh
     * within the background fetch interval skips the network and loads it instead.
     */
    private static final class FreshCacheManager implements GbCacheManager {
        private final String payload;

        FreshCacheManager(String payload) {
            this.payload = payload;
        }

        @Override
        public void saveContent(String key, String data) {
        }

        @Override
        public String loadCache(String key) {
            return payload;
        }

        @Override
        public void clearCache() {
        }

        @Override
        public Long getLastUpdatedMillis(String key) {
            return System.currentTimeMillis();
        }
    }

    private static final class GatedResponseTransformer extends ResponseTransformer {
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            try {
                release.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return response;
        }

        @Override
        public String getName() {
            return "gated-response";
        }
    }
}
