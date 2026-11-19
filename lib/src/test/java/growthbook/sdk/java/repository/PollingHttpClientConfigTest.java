package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the polling HTTP client contract: OkHttp must be allowed to recover from a stale pooled
 * connection on its own. A GrowthBook Proxy advertises a short keep-alive (for example
 * {@code Keep-Alive: timeout=5}) on conditional responses while the SDK polls roughly once per TTL,
 * so the proxy closes the idle socket long before the next poll. With connection-failure recovery
 * disabled OkHttp reuses that dead socket and the poll fails with
 * {@code unexpected end of stream} / {@code EOFException} before the application-level retry runs.
 */
class PollingHttpClientConfigTest {

    @Test
    @DisplayName("Verify: the default polling client recovers from stale pooled connections")
    void defaultPollingClientEnablesConnectionFailureRecovery() throws Exception {
        // Given a repository created without a caller-supplied HTTP client
        GBFeaturesRepository repository = newRepository(null);

        // When the polling client is inspected
        OkHttpClient pollingClient = readOkHttpClient(repository);

        // Then OkHttp is allowed to retry a stale pooled connection on a fresh socket
        assertTrue(pollingClient.retryOnConnectionFailure());
    }

    @Test
    @DisplayName("Verify: a caller-supplied client keeps its enabled connection-failure recovery")
    void preservesCallerClientWithConnectionFailureRecoveryEnabled() throws Exception {
        // Given a caller-supplied client that opts into connection-failure recovery
        OkHttpClient provided = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();

        // When the repository is built with it
        GBFeaturesRepository repository = newRepository(provided);

        // Then the setting is not downgraded
        assertTrue(readOkHttpClient(repository).retryOnConnectionFailure());
    }

    @Test
    @DisplayName("Verify: a caller-supplied client keeps its disabled connection-failure recovery")
    void preservesCallerClientWithConnectionFailureRecoveryDisabled() throws Exception {
        // Given a caller-supplied client that opts out of connection-failure recovery
        OkHttpClient provided = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .build();

        // When the repository is built with it
        GBFeaturesRepository repository = newRepository(provided);

        // Then the caller's choice is honored
        assertFalse(readOkHttpClient(repository).retryOnConnectionFailure());
    }

    private static GBFeaturesRepository newRepository(OkHttpClient okHttpClient) {
        return new GBFeaturesRepository(
                "http://localhost:3300",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                60,
                okHttpClient,
                true,
                null,
                null,
                null,
                null
        );
    }

    private static OkHttpClient readOkHttpClient(GBFeaturesRepository repository) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField("okHttpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(repository);
    }
}
