package growthbook.sdk.java.repository;

import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Options for warming the process-shared feature payload cache via
 * {@link GBFeaturesRepository#prefetchPayload(PrefetchOptions)}.
 *
 * <p>Mirrors the subset of repository configuration that identifies and authorizes a features fetch.
 * A prefetch resolves the same {@code (apiHost, clientKey)} shared-cache key that later
 * {@link GBFeaturesRepository} instances read from, so a boot-time prefetch lets the first real
 * request serve from cache without a network round trip.
 */
@Getter
@Builder
public class PrefetchOptions {

    /**
     * Your client key, e.g. {@code sdk-abc123}. Required.
     */
    private final String clientKey;

    /**
     * The GrowthBook API host. Defaults to the SDK default host when {@code null}.
     */
    @Nullable
    private final String apiHost;

    /**
     * Optional key for decrypting an encrypted features payload.
     */
    @Nullable
    private final String decryptionKey;

    /**
     * When true, bypasses the shared cache and forces a network fetch, refreshing the shared entry even
     * if a fresh one exists. Mirrors the JS SDK's {@code prefetchPayload({ skipCache })}.
     */
    private final boolean skipCache;
}

