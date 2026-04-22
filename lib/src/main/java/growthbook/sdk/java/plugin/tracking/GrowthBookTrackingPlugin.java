package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Batches experiment/feature evaluation events and POSTs them to the
 * GrowthBook data-warehouse ingest endpoint. Wire contract mirrors the Go
 * SDK: {@code POST {ingestorHost}/events} with JSON body
 * {@code {"client_key": ..., "events": [...]}}.
 *
 * <p>A flush is triggered when either the buffer reaches
 * {@link TrackingPluginConfig#resolvedBatchSize()} or a timer fires after
 * {@link TrackingPluginConfig#resolvedBatchTimeout()}. {@link #close()}
 * performs a final synchronous flush.
 *
 * <p>If {@link TrackingPluginConfig#getClientKey()} is null/empty the plugin
 * becomes a no-op: event methods return immediately, no HTTP traffic occurs,
 * and {@link #close()} still completes cleanly.
 */
@Slf4j
public final class GrowthBookTrackingPlugin implements GrowthBookPlugin {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final TrackingPluginConfig config;
    private final OkHttpClient httpClient;
    private final boolean ownsHttpClient;
    private final ScheduledExecutorService scheduler;
    private final Executor flushExecutor;
    private final boolean ownsFlushExecutor;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<TrackingEvent> buffer = new ArrayList<>();
    @Nullable
    private ScheduledFuture<?> pendingFlush;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final boolean disabled;

    private GrowthBookTrackingPlugin(TrackingPluginConfig config) {
        this.config = config;
        this.disabled = config.getClientKey() == null || config.getClientKey().isEmpty();

        if (config.getHttpClient() != null) {
            this.httpClient = config.getHttpClient();
            this.ownsHttpClient = false;
        } else {
            this.httpClient = new OkHttpClient();
            this.ownsHttpClient = true;
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                daemonFactory("growthbook-tracking-plugin-scheduler"));

        if (config.getFlushExecutor() != null) {
            this.flushExecutor = config.getFlushExecutor();
            this.ownsFlushExecutor = false;
        } else {
            this.flushExecutor = Executors.newSingleThreadExecutor(
                    daemonFactory("growthbook-tracking-plugin-flush"));
            this.ownsFlushExecutor = true;
        }
    }

    public static GrowthBookTrackingPlugin of(TrackingPluginConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("TrackingPluginConfig must not be null");
        }
        return new GrowthBookTrackingPlugin(config);
    }

    @Override
    public void init() {
        if (disabled) {
            log.warn("GrowthBookTrackingPlugin disabled: clientKey is null or empty; events will not be sent.");
        }
    }

    @Override
    public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
        if (disabled || closed.get()) return;
        enqueue(TrackingEvent.forExperiment(experiment, result, null));
    }

    @Override
    public <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result) {
        if (disabled || closed.get()) return;
        enqueue(TrackingEvent.forFeature(featureKey, result, null));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        ScheduledFuture<?> pending;
        List<TrackingEvent> toFlush;
        lock.lock();
        try {
            pending = this.pendingFlush;
            this.pendingFlush = null;
            toFlush = drainLocked();
        } finally {
            lock.unlock();
        }

        if (pending != null) {
            pending.cancel(false);
        }

        if (!toFlush.isEmpty()) {
            try {
                flushBatch(toFlush);
            } catch (Throwable t) {
                log.warn("Final tracking flush failed", t);
            }
        }

        scheduler.shutdownNow();
        if (ownsFlushExecutor && flushExecutor instanceof ExecutorService) {
            ExecutorService es = (ExecutorService) flushExecutor;
            es.shutdown();
            try {
                if (!es.awaitTermination(config.resolvedCloseTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    es.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                es.shutdownNow();
            }
        }
        if (ownsHttpClient) {
            try {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
            } catch (Throwable t) {
                log.debug("Failed to shut down owned OkHttpClient", t);
            }
        }
    }

    // --- internal ---

    private void enqueue(TrackingEvent event) {
        List<TrackingEvent> eagerFlush = null;
        lock.lock();
        try {
            buffer.add(event);
            int batchSize = config.resolvedBatchSize();
            if (buffer.size() >= batchSize) {
                eagerFlush = drainLocked();
                if (pendingFlush != null) {
                    pendingFlush.cancel(false);
                    pendingFlush = null;
                }
            } else if (pendingFlush == null) {
                Duration timeout = config.resolvedBatchTimeout();
                pendingFlush = scheduler.schedule(this::scheduledFlush,
                        timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } finally {
            lock.unlock();
        }

        if (eagerFlush != null && !eagerFlush.isEmpty()) {
            submitFlush(eagerFlush);
        }
    }

    private void scheduledFlush() {
        if (closed.get()) return;
        List<TrackingEvent> toFlush;
        lock.lock();
        try {
            pendingFlush = null;
            toFlush = drainLocked();
        } finally {
            lock.unlock();
        }
        if (!toFlush.isEmpty()) {
            submitFlush(toFlush);
        }
    }

    private List<TrackingEvent> drainLocked() {
        if (buffer.isEmpty()) return Collections.emptyList();
        List<TrackingEvent> out = new ArrayList<>(buffer);
        buffer.clear();
        return out;
    }

    private void submitFlush(List<TrackingEvent> batch) {
        try {
            flushExecutor.execute(() -> {
                try {
                    flushBatch(batch);
                } catch (Throwable t) {
                    log.warn("Tracking flush failed", t);
                }
            });
        } catch (RejectedExecutionException e) {
            try {
                flushBatch(batch);
            } catch (Throwable t) {
                log.warn("Inline tracking flush failed", t);
            }
        }
    }

    private void flushBatch(List<TrackingEvent> batch) {
        if (batch.isEmpty() || disabled) return;

        JsonObject body = new JsonObject();
        body.addProperty("client_key", config.getClientKey());
        JsonArray events = new JsonArray();
        for (TrackingEvent e : batch) {
            events.add(GrowthBookJsonUtils.getInstance().gson.toJsonTree(e));
        }
        body.add("events", events);

        String url = config.resolvedIngestorHost() + "/events";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", SdkMetadata.USER_AGENT)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Tracking ingest POST {} returned status {}", url, response.code());
            }
        } catch (IOException e) {
            log.warn("Tracking ingest POST {} failed: {}", url, e.toString());
        }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix + "-" + THREAD_COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
