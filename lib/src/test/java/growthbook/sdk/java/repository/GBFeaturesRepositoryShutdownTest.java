package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

/**
 * Lifecycle behavior of {@link GBFeaturesRepository#shutdown()} around the HTTP client it uses for
 * feature fetches: a client the repository created is fully released, while a caller-supplied client
 * (whose lifecycle the caller owns) is left running.
 */
class GBFeaturesRepositoryShutdownTest {

    @Test
    @DisplayName("Verify: shutdown releases the HTTP client the repository created")
    void shutdownReleasesOwnedHttpClient() throws Exception {
        // Given a repository that created its own HTTP client (none supplied)
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-owned")
                .isCacheDisabled(true)
                .build();
        OkHttpClient owned = readHttpClient(repository);

        // When it is shut down
        repository.shutdown();

        // Then the owned client's dispatcher executor is shut down (its threads can exit)
        assertTrue(owned.dispatcher().executorService().isShutdown());
    }

    @Test
    @DisplayName("Verify: shutdown does not release a caller-supplied HTTP client")
    void shutdownLeavesSuppliedHttpClientRunning() {
        // Given a caller-supplied HTTP client
        OkHttpClient supplied = new OkHttpClient.Builder().build();
        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-supplied")
                .isCacheDisabled(true)
                .okHttpClient(supplied)
                .build();

        // When it is shut down
        repository.shutdown();

        // Then the caller's client keeps running — its lifecycle belongs to the caller
        assertFalse(supplied.dispatcher().executorService().isShutdown());

        // Cleanup: release the caller-owned client so the test leaves no live threads
        supplied.dispatcher().executorService().shutdown();
        supplied.connectionPool().evictAll();
    }

    private static OkHttpClient readHttpClient(GBFeaturesRepository repository) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField("okHttpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(repository);
    }
}
