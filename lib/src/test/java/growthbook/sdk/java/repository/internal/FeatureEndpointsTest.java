package growthbook.sdk.java.repository.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeatureEndpointsTest {

    private static final String HOST = "https://gb.example.test";
    private static final String CLIENT_KEY = "sdk-placeholder-key";

    @Test
    @DisplayName("Builds the features, events and remote eval endpoints from host and client key")
    void buildsEndpointsFromHostAndClientKey() {
        // Given
        // When
        FeatureEndpoints endpoints = FeatureEndpoints.of(HOST, CLIENT_KEY);

        // Then
        assertEquals(HOST + "/api/features/" + CLIENT_KEY, endpoints.features());
        assertEquals(HOST + "/sub/" + CLIENT_KEY, endpoints.events());
        assertEquals(HOST + "/api/eval/" + CLIENT_KEY, endpoints.remoteEval());
    }

    @Test
    @DisplayName("Uses the default GrowthBook CDN when the host is null")
    void usesDefaultHostWhenHostIsNull() {
        // Given
        // When
        FeatureEndpoints endpoints = FeatureEndpoints.of(null, CLIENT_KEY);

        // Then
        assertEquals("https://cdn.growthbook.io/api/features/" + CLIENT_KEY, endpoints.features());
        assertEquals("https://cdn.growthbook.io/sub/" + CLIENT_KEY, endpoints.events());
        assertEquals("https://cdn.growthbook.io/api/eval/" + CLIENT_KEY, endpoints.remoteEval());
    }

    @Test
    @DisplayName("Rejects a null client key")
    void rejectsNullClientKey() {
        // Given
        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FeatureEndpoints.of(HOST, null));

        // Then
        assertEquals("clientKey cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Derives the same cache key for the same endpoint")
    void derivesStableCacheKey() {
        // Given
        FeatureEndpoints first = FeatureEndpoints.of(HOST, CLIENT_KEY);

        // When
        FeatureEndpoints second = FeatureEndpoints.of(HOST, CLIENT_KEY);

        // Then
        assertEquals(first.cacheKey(), second.cacheKey());
    }

    @Test
    @DisplayName("Derives different cache keys for different client keys and hosts")
    void derivesDifferentCacheKeysAcrossEndpoints() {
        // Given
        FeatureEndpoints base = FeatureEndpoints.of(HOST, CLIENT_KEY);

        // When
        FeatureEndpoints otherKey = FeatureEndpoints.of(HOST, "sdk-other-key");
        FeatureEndpoints otherHost = FeatureEndpoints.of("https://other.example.test", CLIENT_KEY);

        // Then
        assertNotEquals(base.cacheKey(), otherKey.cacheKey());
        assertNotEquals(base.cacheKey(), otherHost.cacheKey());
    }

    @Test
    @DisplayName("Formats the cache key as a prefix, sixteen hex characters and a json extension")
    void formatsCacheKey() {
        // Given
        FeatureEndpoints endpoints = FeatureEndpoints.of(HOST, CLIENT_KEY);

        // When
        String cacheKey = endpoints.cacheKey();

        // Then
        assertTrue(cacheKey.matches("FEATURE_CACHE_[0-9a-f]{16}\\.json"), cacheKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {HOST, "https://cdn.growthbook.io", "http://localhost:3100"})
    @DisplayName("Supports conditional requests for a GrowthBook features URL")
    void supportsConditionalRequestsForFeaturesUrl(String host) {
        // Given
        FeatureEndpoints endpoints = FeatureEndpoints.of(host, CLIENT_KEY);

        // When
        boolean supported = endpoints.supportsConditionalRequests();

        // Then
        assertTrue(supported);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sdk-key/extra"})
    @DisplayName("Does not support conditional requests when the features URL has no single key segment")
    void doesNotSupportConditionalRequestsForOtherUrls(String clientKey) {
        // Given
        FeatureEndpoints endpoints = FeatureEndpoints.of(HOST, clientKey);

        // When
        boolean supported = endpoints.supportsConditionalRequests();

        // Then
        assertFalse(supported);
    }
}
