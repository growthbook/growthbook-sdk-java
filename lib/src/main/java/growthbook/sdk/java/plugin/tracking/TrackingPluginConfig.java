package growthbook.sdk.java.plugin.tracking;

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
 */
@Getter
@Builder
public final class TrackingPluginConfig {

    public static final String DEFAULT_INGESTOR_HOST = "https://us1.gb-ingest.com";
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final Duration DEFAULT_BATCH_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5);

    /** Base URL of the ingest endpoint. Events POST to {@code /events}. */
    @Nullable
    private final String ingestorHost;

    /**
     * Client key (SDK connection key). If null/empty the plugin becomes a
     * no-op — it will not make HTTP requests but {@link GrowthBookTrackingPlugin#close()}
     * still completes cleanly.
     */
    @Nullable
    private final String clientKey;

    /** Max events buffered before an eager flush. */
    @Nullable
    private final Integer batchSize;

    /** Max time an event sits in the buffer before a scheduled flush. */
    @Nullable
    private final Duration batchTimeout;

    /** Max time {@link GrowthBookTrackingPlugin#close()} waits for the final flush. */
    @Nullable
    private final Duration closeTimeout;

    /** Optional HTTP client override. Defaults to a fresh OkHttpClient. */
    @Nullable
    private final OkHttpClient httpClient;

    /**
     * Optional executor used to POST batches off the caller thread. Defaults
     * to a single-thread daemon executor created by the plugin.
     */
    @Nullable
    private final Executor flushExecutor;

    public String resolvedIngestorHost() {
        if (ingestorHost == null || ingestorHost.isEmpty()) return DEFAULT_INGESTOR_HOST;
        return stripTrailingSlash(ingestorHost);
    }

    public int resolvedBatchSize() {
        return batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }

    public Duration resolvedBatchTimeout() {
        return batchTimeout == null || batchTimeout.isZero() || batchTimeout.isNegative()
                ? DEFAULT_BATCH_TIMEOUT : batchTimeout;
    }

    public Duration resolvedCloseTimeout() {
        return closeTimeout == null || closeTimeout.isZero() || closeTimeout.isNegative()
                ? DEFAULT_CLOSE_TIMEOUT : closeTimeout;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
