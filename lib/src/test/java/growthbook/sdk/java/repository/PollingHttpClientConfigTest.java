package growthbook.sdk.java.repository;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that the polling HTTP client may recover from a stale pooled connection closed by a short proxy keep-alive.
 */
class PollingHttpClientConfigTest {

    @Test
    @DisplayName("The default polling client recovers from stale pooled connections")
    void defaultPollingClientEnablesConnectionFailureRecovery() {
        // Given
        GBFeaturesRepository repository = newRepository(null);

        // When
        OkHttpClient pollingClient = RepositoryInternals.transport(repository).httpClient();

        // Then
        assertTrue(pollingClient.retryOnConnectionFailure());
    }

    @ParameterizedTest(name = "retryOnConnectionFailure={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("A caller-supplied client keeps its connection-failure recovery setting")
    void callerSuppliedClientKeepsConnectionFailureRecoverySetting(boolean retryOnConnectionFailure) {
        // Given
        OkHttpClient provided = new OkHttpClient.Builder()
                .retryOnConnectionFailure(retryOnConnectionFailure)
                .build();

        // When
        GBFeaturesRepository repository = newRepository(provided);

        // Then
        OkHttpClient pollingClient = RepositoryInternals.transport(repository).httpClient();
        assertEquals(retryOnConnectionFailure, pollingClient.retryOnConnectionFailure());
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
}
