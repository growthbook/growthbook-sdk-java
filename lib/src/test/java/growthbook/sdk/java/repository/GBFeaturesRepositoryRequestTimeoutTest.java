package growthbook.sdk.java.repository;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@code requestTimeout} configures the SDK-managed features client deterministically:
 * the whole-call timeout ({@code callTimeout}) and connect/read/write are all bounded, the default is
 * applied when unset, {@link Duration#ZERO} opts out, and a negative value is rejected.
 */
class GBFeaturesRepositoryRequestTimeoutTest {

    private static final String API_HOST = "https://cdn.growthbook.io";
    private static final String CLIENT_KEY = "sdk-request-timeout";

    @Test
    @DisplayName("Verify: an explicit requestTimeout bounds the whole call and connect/read/write")
    void explicitTimeoutBoundsCall() throws Exception {
        OkHttpClient client = featuresClient(GBFeaturesRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ofMillis(300))
                .build());

        assertEquals(300, client.callTimeoutMillis());
        assertEquals(300, client.connectTimeoutMillis());
        assertEquals(300, client.readTimeoutMillis());
        assertEquals(300, client.writeTimeoutMillis());
    }

    @Test
    @DisplayName("Verify: an unset requestTimeout keeps OkHttp's defaults: no whole-call timeout")
    void unsetTimeoutKeepsOkHttpDefaults() throws Exception {
        OkHttpClient client = featuresClient(GBFeaturesRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .build());

        assertEquals(0, client.callTimeoutMillis());
        assertEquals(10_000, client.connectTimeoutMillis());
        assertEquals(10_000, client.readTimeoutMillis());
        assertEquals(10_000, client.writeTimeoutMillis());
    }

    @Test
    @DisplayName("Verify: Duration.ZERO restores an infinite timeout on the features client")
    void zeroTimeoutIsInfinite() throws Exception {
        OkHttpClient client = featuresClient(GBFeaturesRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ZERO)
                .build());

        assertEquals(0, client.callTimeoutMillis());
        assertEquals(0, client.connectTimeoutMillis());
        assertEquals(0, client.readTimeoutMillis());
        assertEquals(0, client.writeTimeoutMillis());
    }

    @Test
    @DisplayName("Verify: a negative requestTimeout is rejected at construction")
    void rejectsNegativeRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> GBFeaturesRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ofMillis(-1))
                .build());
    }

    @Test
    @DisplayName("A sub-millisecond requestTimeout is rejected instead of silently meaning no timeout")
    void rejectsSubMillisecondRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> GBFeaturesRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ofNanos(999_999))
                .build());
    }

    @Test
    @DisplayName("An overflowing requestTimeout is rejected with IllegalArgumentException, not ArithmeticException")
    void rejectsOverflowingRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () -> GBFeaturesRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .requestTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                .build());
    }

    @Test
    @DisplayName("The SSE stream client is never bounded by a timeout")
    void sseClientIsUnbounded() {
        OkHttpClient client = GBFeaturesRepository.newSseHttpClient();

        assertEquals(0, client.callTimeoutMillis());
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
