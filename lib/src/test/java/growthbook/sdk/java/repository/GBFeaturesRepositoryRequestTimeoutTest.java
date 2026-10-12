package growthbook.sdk.java.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Verifies that a configured {@code requestTimeout} bounds the features fetch, so initialization
 * against a stalling endpoint fails fast instead of blocking indefinitely.
 */
class GBFeaturesRepositoryRequestTimeoutTest {

    private static final String CLIENT_KEY = "sdk-request-timeout";

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10_000)
                        .withBody("{\"features\":{}}")));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @DisplayName("Verify: a configured requestTimeout fails initialization fast when the endpoint stalls")
    void failsFastWhenEndpointStalls() {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .retryPolicy(new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO))
                .requestTimeout(Duration.ofMillis(300))
                .build();

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(FeatureFetchException.class, subject::initialize));
        } finally {
            subject.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: a negative requestTimeout is rejected at construction")
    void rejectsNegativeRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ofMillis(-1))
                .build());
    }

    @Test
    @DisplayName("Verify: an unset requestTimeout applies the 10s default to the features client")
    void defaultTimeoutAppliedWhenUnset() throws Exception {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .build();

        OkHttpClient client = featuresClient(subject);
        assertEquals(10_000, client.connectTimeoutMillis());
        assertEquals(10_000, client.readTimeoutMillis());
        assertEquals(10_000, client.writeTimeoutMillis());
    }

    @Test
    @DisplayName("Verify: Duration.ZERO restores an infinite timeout on the features client")
    void zeroTimeoutIsInfinite() throws Exception {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ZERO)
                .build();

        OkHttpClient client = featuresClient(subject);
        assertEquals(0, client.connectTimeoutMillis());
        assertEquals(0, client.readTimeoutMillis());
        assertEquals(0, client.writeTimeoutMillis());
    }

    private static OkHttpClient featuresClient(GBFeaturesRepository subject) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField("okHttpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(subject);
    }
}
