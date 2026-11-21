package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.remoteeval.RemoteEvalEndpoints;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static growthbook.sdk.java.constants.SDKConstants.Endpoints.DEFAULT_API_HOST;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.FEATURES_ENDPOINT_PATH;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.FEATURES_ENDPOINT_PATTERN;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.STREAMING_ENDPOINT_PATH;

/**
 * The URLs a repository talks to for one client key, and the cache key derived from them.
 * Immutable.
 */
public final class FeatureEndpoints {

    private static final String CACHE_KEY_PREFIX = "FEATURE_CACHE_";
    private static final String LEGACY_CACHE_KEY = "FEATURE_CACHE.json";

    private final String features;
    private final String events;
    private final String remoteEval;
    private final String cacheKey;

    private FeatureEndpoints(String features, String events, String remoteEval) {
        this.features = features;
        this.events = events;
        this.remoteEval = remoteEval;
        this.cacheKey = cacheKeyFor(features);
    }

    /**
     * @param apiHost   the GrowthBook API host, or {@code null} for the default CDN
     * @param clientKey the SDK client key
     * @return the endpoints for that client
     * @throws IllegalArgumentException when {@code clientKey} is {@code null}
     */
    public static FeatureEndpoints of(@Nullable String apiHost, String clientKey) {
        if (clientKey == null) {
            throw new IllegalArgumentException("clientKey cannot be null");
        }
        String host = apiHost == null ? DEFAULT_API_HOST : apiHost;
        return new FeatureEndpoints(
                host + FEATURES_ENDPOINT_PATH + clientKey,
                host + STREAMING_ENDPOINT_PATH + clientKey,
                RemoteEvalEndpoints.evalEndpoint(host, clientKey));
    }

    public String features() {
        return features;
    }

    public String events() {
        return events;
    }

    public String remoteEval() {
        return remoteEval;
    }

    /**
     * A key scoped to the features endpoint, so SDK instances for different clients never share a
     * cached payload in a shared cache store.
     */
    public String cacheKey() {
        return cacheKey;
    }

    /**
     * Whether the features endpoint is a GrowthBook features URL, which honours ETags and
     * cache-control headers.
     */
    public boolean supportsConditionalRequests() {
        return features.matches(FEATURES_ENDPOINT_PATTERN);
    }

    private static String cacheKeyFor(String featuresEndpoint) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(featuresEndpoint.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return CACHE_KEY_PREFIX + hex + ".json";
        } catch (NoSuchAlgorithmException e) {
            return LEGACY_CACHE_KEY;
        }
    }
}
