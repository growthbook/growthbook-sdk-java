package growthbook.sdk.java.plugin.tracking;

import growthbook.sdk.java.util.StringUtils;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import okhttp3.OkHttpClient;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Configuration for {@link GrowthBookTrackingPlugin}. Defaults mirror the
 * GrowthBook Go SDK: POST {@code {ingestorHost}/events}, batch size 100,
 * flush every 10 seconds.
 *
 * <p>Settings with defaults are exposed only through their {@code resolved*()}
 * accessors so callers always read the effective value; the raw nullable
 * fields are intentionally not exposed.
 */
@Builder
public final class TrackingPluginConfig {

    public static final String DEFAULT_INGESTOR_HOST = "https://us1.gb-ingest.com";
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int MAX_BATCH_SIZE = 10_000;
    public static final Duration DEFAULT_BATCH_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5);

    /** Base URL of the ingest endpoint. Events POST to {@code /events}. */
    @Getter(AccessLevel.NONE)
    @Nullable
    private final String ingestorHost;

    /**
     * Client key (SDK connection key). If blank the plugin becomes a no-op —
     * it will not make HTTP requests but {@link GrowthBookTrackingPlugin#close()}
     * still completes cleanly.
     */
    @Getter
    @Nullable
    private final String clientKey;

    /** Max events buffered before an eager flush. */
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Integer batchSize;

    /** Max time an event sits in the buffer before a scheduled flush. */
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Duration batchTimeout;

    /**
     * Time budget for {@link GrowthBookTrackingPlugin#close()}. It bounds both the synchronous
     * final POST (applied as an OkHttp call timeout on a client derived from the configured one)
     * and the wait for any already-submitted async batches to complete.
     */
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Duration closeTimeout;

    /** Optional HTTP client override. Defaults to a fresh OkHttpClient. */
    @Getter
    @Nullable
    private final OkHttpClient httpClient;

    /**
     * Optional executor used to POST batches off the caller thread. Defaults
     * to a single-thread daemon executor created by the plugin.
     */
    @Getter
    @Nullable
    private final Executor flushExecutor;

    public String resolvedIngestorHost() {
        if (StringUtils.isBlank(ingestorHost)) {
            return DEFAULT_INGESTOR_HOST;
        }
        return stripTrailingSlash(ingestorHost);
    }

    public int resolvedBatchSize() {
        if (batchSize == null || batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    public Duration resolvedBatchTimeout() {
        return batchTimeout == null || batchTimeout.isZero() || batchTimeout.isNegative()
                ? DEFAULT_BATCH_TIMEOUT : batchTimeout;
    }

    public Duration resolvedCloseTimeout() {
        return closeTimeout == null || closeTimeout.isZero() || closeTimeout.isNegative()
                ? DEFAULT_CLOSE_TIMEOUT : closeTimeout;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
