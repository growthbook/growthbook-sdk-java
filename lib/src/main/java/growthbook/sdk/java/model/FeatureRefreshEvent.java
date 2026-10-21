package growthbook.sdk.java.model;

import java.time.Instant;
import javax.annotation.Nullable;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import lombok.Getter;
import lombok.ToString;

/**
 * Metadata-only event delivered to {@link FeatureRefreshListener}s after a feature refresh attempt.
 * A cache fallback after an upstream failure is unsuccessful with {@code loadedFromCache} set to true.
 *
 * <p><b>Counting refreshes:</b> an event is published per refresh <em>attempt</em>, not per change,
 * so metrics that should track definition updates must filter on {@link #isFeaturesChanged()}.
 * This matters most on {@link FeatureRefreshSource#SSE}: a server event that carries no payload is
 * published as successful with {@code featuresChanged == false}. Named keep-alives
 * ({@code heartbeat}, {@code keepalive}, {@code ping}) are filtered out before reaching listeners,
 * but a payload-less event under any other name is indistinguishable from a genuine
 * "definitions changed, refetch" signal, so it is delivered rather than dropped — the remote-eval
 * cache invalidation depends on receiving it. GrowthBook Cloud emits such events roughly every
 * 30 seconds, so counting raw events there overstates the refresh rate.
 *
 * <p>This is an immutable value carrier; identity equality is used. It intentionally does not define
 * field-based {@code equals}/{@code hashCode}, since the {@code error} and {@code timestamp} fields
 * make two refresh attempts effectively never equal.
 */
@Getter
@ToString
public class FeatureRefreshEvent {
    private final boolean successful;
    private final boolean featuresChanged;
    private final boolean loadedFromCache;
    @Nullable
    private final Throwable error;
    private final int activeFeatureCount;
    private final FeatureRefreshSource source;
    @Nullable
    private final FeatureRefreshStrategy refreshStrategy;
    private final long durationMillis;
    private final Instant timestamp;

    public FeatureRefreshEvent(
            boolean successful,
            boolean featuresChanged,
            boolean loadedFromCache,
            @Nullable Throwable error,
            int activeFeatureCount,
            FeatureRefreshSource source,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            long durationMillis,
            Instant timestamp
    ) {
        this.successful = successful;
        this.featuresChanged = featuresChanged;
        this.loadedFromCache = loadedFromCache;
        this.error = error;
        this.activeFeatureCount = activeFeatureCount;
        this.source = source;
        this.refreshStrategy = refreshStrategy;
        this.durationMillis = Math.max(0, durationMillis);
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static FeatureRefreshEvent success(
            boolean featuresChanged,
            boolean loadedFromCache,
            int activeFeatureCount,
            FeatureRefreshSource source,
            @Nullable FeatureRefreshStrategy refreshStrategy
    ) {
        return success(
                featuresChanged,
                loadedFromCache,
                activeFeatureCount,
                source,
                refreshStrategy,
                0
        );
    }

    public static FeatureRefreshEvent success(
            boolean featuresChanged,
            boolean loadedFromCache,
            int activeFeatureCount,
            FeatureRefreshSource source,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            long durationMillis
    ) {
        return new FeatureRefreshEvent(
                true,
                featuresChanged,
                loadedFromCache,
                null,
                activeFeatureCount,
                source,
                refreshStrategy,
                durationMillis,
                Instant.now()
        );
    }

    public static FeatureRefreshEvent failure(
            Throwable error,
            boolean featuresChanged,
            boolean loadedFromCache,
            int activeFeatureCount,
            FeatureRefreshSource source,
            @Nullable FeatureRefreshStrategy refreshStrategy
    ) {
        return failure(
                error,
                featuresChanged,
                loadedFromCache,
                activeFeatureCount,
                source,
                refreshStrategy,
                0
        );
    }

    public static FeatureRefreshEvent failure(
            Throwable error,
            boolean featuresChanged,
            boolean loadedFromCache,
            int activeFeatureCount,
            FeatureRefreshSource source,
            @Nullable FeatureRefreshStrategy refreshStrategy,
            long durationMillis
    ) {
        return new FeatureRefreshEvent(
                false,
                featuresChanged,
                loadedFromCache,
                error,
                activeFeatureCount,
                source,
                refreshStrategy,
                durationMillis,
                Instant.now()
        );
    }
}
