package growthbook.sdk.java.remoteeval;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.UncheckedExecutionException;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory cache for remote-eval responses.
 *
 * <p>Backed by a Guava {@link LoadingCache}: served fresh within {@code staleTtl}, served stale
 * with a background refetch up to {@code cacheTtl}, refetched after that. Guava coalesces
 * concurrent reads for the same key into a single fetch.
 */
@Slf4j
public final class RemoteEvalCache {

    private final LoadingCache<Key, RemoteEvalResponse> cache;

    @Nullable
    private final ExecutorService refreshExecutor;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RemoteEvalCache(
            RemoteEvalService service,
            int maximumSize,
            @Nullable Duration staleTtl,
            @Nullable Duration cacheTtl
    ) {
        this(service, maximumSize, staleTtl, cacheTtl, null);
    }

    /**
     * @param service    performs the actual remote-eval POST.
     * @param maximumSize LRU bound on cached responses (values {@code <= 0} disable retention).
     * @param staleTtl   when positive, enables stale-while-revalidate: entries older than this
     *                   are served stale while refreshed in the background. {@code null} disables SWR.
     * @param cacheTtl   when positive, the hard expiry after which an entry is dropped and refetched.
     *                   {@code null} means no time-based expiry (entries live until evicted or invalidated).
     * @param ticker     time source; {@code null} uses the system ticker. Injectable for tests.
     */
    public RemoteEvalCache(
            RemoteEvalService service,
            int maximumSize,
            @Nullable Duration staleTtl,
            @Nullable Duration cacheTtl,
            @Nullable Ticker ticker
    ) {
        boolean staleWhileRevalidate = isPositive(staleTtl);
        this.refreshExecutor = staleWhileRevalidate ? newRefreshExecutor() : null;

        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .maximumSize(Math.max(0, maximumSize));
        if (ticker != null) {
            builder.ticker(ticker);
        }
        if (isPositive(cacheTtl)) {
            builder.expireAfterWrite(cacheTtl);
        }
        if (staleWhileRevalidate) {
            builder.refreshAfterWrite(staleTtl);
        }

        this.cache = builder.build(new CacheLoader<Key, RemoteEvalResponse>() {
            @Override
            public RemoteEvalResponse load(Key key) throws FeatureFetchException {
                return service.fetch(key.payload);
            }

            @Override
            public ListenableFuture<RemoteEvalResponse> reload(Key key, RemoteEvalResponse oldValue) {
                if (closed.get()) {
                    return Futures.immediateFuture(oldValue);
                }
                ListenableFutureTask<RemoteEvalResponse> task =
                        ListenableFutureTask.create(() -> service.fetch(key.payload));
                refreshExecutor.execute(task);
                return task;
            }
        });
    }

    public RemoteEvalResponse get(String cacheKey, RequestBodyForRemoteEval payload) throws FeatureFetchException {
        if (closed.get()) {
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                    "Remote evaluation cache is closed"
            );
        }
        try {
            return cache.get(new Key(cacheKey, payload));
        } catch (ExecutionException | UncheckedExecutionException e) {
            throw toFeatureFetchException(e);
        }
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public void shutdown() {
        closed.set(true);
        cache.invalidateAll();
        if (refreshExecutor != null) {
            refreshExecutor.shutdownNow();
        }
    }

    private static boolean isPositive(@Nullable Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private static FeatureFetchException toFeatureFetchException(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        if (cause instanceof FeatureFetchException) {
            return (FeatureFetchException) cause;
        }
        return new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                cause.getMessage()
        );
    }

    private static ExecutorService newRefreshExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "growthbook-remote-eval-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Identity is the cache key only; the payload rides along for the loader but is deliberately
     * excluded from equals/hashCode (same cache key means same user).
     */
    private static final class Key {
        private final String cacheKey;
        private final RequestBodyForRemoteEval payload;

        Key(String cacheKey, RequestBodyForRemoteEval payload) {
            this.cacheKey = cacheKey;
            this.payload = payload;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            return cacheKey.equals(((Key) o).cacheKey);
        }

        @Override
        public int hashCode() {
            return cacheKey.hashCode();
        }
    }
}
