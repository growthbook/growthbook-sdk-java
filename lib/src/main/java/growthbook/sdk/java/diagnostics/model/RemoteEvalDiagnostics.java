package growthbook.sdk.java.diagnostics.model;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;

/**
 * Remote evaluation metadata.
 */
@Value
@Builder
public class RemoteEvalDiagnostics {
    /**
     * Whether remote evaluation is enabled by configuration.
     */
    boolean enabled;

    /**
     * Whether remote-eval services are initialized and ready.
     */
    boolean ready;

    /**
     * Whether the remote-eval response cache has been created.
     */
    boolean cacheConfigured;

    /**
     * Configured maximum number of cached remote-eval responses.
     */
    int cacheMaxSize;

    /**
     * Configured hard TTL in seconds for remote-eval cache entries.
     */
    @Nullable
    Integer cacheTtlSeconds;
}
