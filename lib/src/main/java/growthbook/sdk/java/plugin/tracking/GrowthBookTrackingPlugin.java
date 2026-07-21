package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
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
 * <p>Resources (HTTP client, scheduler, flush executor) are created in
 * {@link #init()}, not the constructor, so an unregistered plugin never leaks
 * threads. A flush is triggered when either the buffer reaches
 * {@link TrackingPluginConfig#resolvedBatchSize()} or a timer fires after
 * {@link TrackingPluginConfig#resolvedBatchTimeout()}. {@link #close()}
 * performs a final synchronous flush.
 *
 * <p>Delivery is best-effort: the owned flush executor uses a bounded queue and
 * drops the newest batch (with a warning) under sustained backpressure, and
 * failed POSTs are logged but not retried. If the configured {@code clientKey}
 * is blank the plugin is disabled: no resources are created, event methods are
 * no-ops, and {@link #close()} still completes cleanly.
 */
@Slf4j
public final class GrowthBookTrackingPlugin implements GrowthBookPlugin {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    /** Maximum number of pending flush batches before the newest is dropped. */
    private static final int MAX_PENDING_FLUSH_BATCHES = 256;

    private final TrackingPluginConfig config;
    private final boolean disabled;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<TrackingEvent> buffer = new ArrayList<>();
    @Nullable
    private ScheduledFuture<?> pendingFlush;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Assigned in init(); read only after initialized is observed true.
    private volatile OkHttpClient httpClient;
    private volatile boolean ownsHttpClient;
    private volatile ScheduledExecutorService scheduler;
    private volatile Executor flushExecutor;
    private volatile boolean ownsFlushExecutor;

    private GrowthBookTrackingPlugin(TrackingPluginConfig config) {
        this.config = config;
        this.disabled = StringUtils.isBlank(config.getClientKey());
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
            log.warn("GrowthBookTrackingPlugin disabled: clientKey is blank; events will not be sent.");
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        OkHttpClient providedClient = config.getHttpClient();
        if (providedClient != null) {
            this.httpClient = providedClient;
            this.ownsHttpClient = false;
        } else {
            this.httpClient = new OkHttpClient();
            this.ownsHttpClient = true;
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                daemonFactory("growthbook-tracking-plugin-scheduler"));

        Executor providedExecutor = config.getFlushExecutor();
        if (providedExecutor != null) {
            this.flushExecutor = providedExecutor;
            this.ownsFlushExecutor = false;
        } else {
            this.flushExecutor = newBoundedFlushExecutor();
            this.ownsFlushExecutor = true;
        }
    }

    @Override
    public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
        if (!isActive()) {
            return;
        }
        enqueue(TrackingEvent.forExperiment(experiment, result));
    }

    @Override
    public <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result) {
        if (!isActive()) {
            return;
        }
        enqueue(TrackingEvent.forFeature(featureKey, result));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        finalFlush();
        shutdownScheduler();
        shutdownFlushExecutor();
        shutdownHttpClient();
    }

    private boolean isActive() {
        return !disabled && initialized.get() && !closed.get();
    }

    private void enqueue(TrackingEvent event) {
        List<TrackingEvent> eagerFlush = null;
        lock.lock();
        try {
            buffer.add(event);
            if (buffer.size() >= config.resolvedBatchSize()) {
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
        if (closed.get()) {
            return;
        }
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
        if (buffer.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrackingEvent> out = new ArrayList<>(buffer);
        buffer.clear();
        return out;
    }

    private void submitFlush(List<TrackingEvent> batch) {
        Executor executor = this.flushExecutor;
        if (executor == null) {
            flushBatch(batch);
            return;
        }
        try {
            executor.execute(() -> flushBatch(batch));
        } catch (RejectedExecutionException e) {
            // Bounded queue full (or a user executor rejected): drop the newest batch.
            log.warn("Tracking flush queue full; dropping batch of {} events", batch.size());
        }
    }

    private void flushBatch(List<TrackingEvent> batch) {
        if (batch.isEmpty() || httpClient == null) {
            return;
        }
        String url = config.resolvedIngestorHost() + "/events";
        try {
            JsonObject body = new JsonObject();
            body.addProperty("client_key", config.getClientKey());
            JsonArray events = new JsonArray();
            for (TrackingEvent event : batch) {
                events.add(GrowthBookJsonUtils.getInstance().gson.toJsonTree(event));
            }
            body.add("events", events);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", SdkMetadata.USER_AGENT)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Tracking ingest POST {} returned status {}", url, response.code());
                }
            }
        } catch (Exception e) {
            log.warn("Tracking ingest POST {} failed: {}", url, e.toString());
        }
    }

    private void finalFlush() {
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
            flushBatch(toFlush);
        }
    }

    private void shutdownScheduler() {
        ScheduledExecutorService s = this.scheduler;
        if (s != null) {
            s.shutdownNow();
        }
    }

    private void shutdownFlushExecutor() {
        if (!ownsFlushExecutor || !(flushExecutor instanceof ExecutorService)) {
            return;
        }
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

    private void shutdownHttpClient() {
        if (!ownsHttpClient || httpClient == null) {
            return;
        }
        try {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        } catch (Exception e) {
            log.debug("Failed to shut down owned OkHttpClient", e);
        }
    }

    private ExecutorService newBoundedFlushExecutor() {
        return new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_PENDING_FLUSH_BATCHES),
                daemonFactory("growthbook-tracking-plugin-flush"),
                (rejected, executor) ->
                        log.warn("Tracking flush queue full ({} batches pending); dropping batch",
                                executor.getQueue().size()));
    }

    private static ThreadFactory daemonFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
