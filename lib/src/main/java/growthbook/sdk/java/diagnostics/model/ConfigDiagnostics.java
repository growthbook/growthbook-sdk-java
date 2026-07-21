package growthbook.sdk.java.diagnostics.model;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;

/**
 * Sanitized SDK configuration metadata.
 */
@Value
@Builder
public class ConfigDiagnostics {
    /**
     * Effective API host used by the feature repository.
     */
    String apiHost;

    /**
     * Feature endpoint with the client key masked.
     */
    @Nullable
    String featuresEndpoint;

    /**
     * Streaming endpoint with the client key masked.
     */
    @Nullable
    String eventsEndpoint;

    /**
     * Masked GrowthBook client key.
     */
    @Nullable
    String clientKeyMasked;

    /**
     * Whether encrypted feature payloads are configured. Secret values are never exposed.
     */
    boolean encryptionConfigured;

    /**
     * Stale-while-revalidate TTL in seconds.
     */
    int swrTtlSeconds;

    /**
     * Optional minimum interval between background refreshes, in milliseconds.
     */
    @Nullable
    Long backgroundFetchIntervalMillis;

    /**
     * Maximum number of feature fetch attempts, including the first attempt.
     */
    int retryMaxAttempts;

    /**
     * Whether evaluations are globally enabled.
     */
    boolean enabled;

    /**
     * Whether QA mode is enabled.
     */
    boolean qaMode;

    /**
     * Whether a sticky bucketing service is configured.
     */
    boolean stickyBucketingEnabled;
}
