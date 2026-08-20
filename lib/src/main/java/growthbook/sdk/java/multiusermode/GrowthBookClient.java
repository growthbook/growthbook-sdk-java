package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.constants.SDKConstants;
import growthbook.sdk.java.diagnostics.model.Diagnostics;
import growthbook.sdk.java.diagnostics.provider.DiagnosticsProvider;
import growthbook.sdk.java.diagnostics.provider.GrowthBookClientDiagnosticsProvider;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.GrowthBookClientInitializationException;
import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.OptionsValidator;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.plugin.PluginRegistry;
import growthbook.sdk.java.remoteeval.RemoteEvalCache;
import growthbook.sdk.java.remoteeval.RemoteEvalCacheKey;
import growthbook.sdk.java.remoteeval.RemoteEvalOptionsValidator;
import growthbook.sdk.java.remoteeval.RemoteEvalRequestBuilder;
import growthbook.sdk.java.remoteeval.RemoteEvalResponse;
import growthbook.sdk.java.remoteeval.RemoteEvalService;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.SyncOffloadStickyBucketAdapter;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final List<ExperimentRunCallback> callbacks;
    private final FeatureEvaluator featureEvaluator;
    private final Map<String, AssignedExperiment> assigned;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private final AtomicReference<GlobalContext> globalContext = new AtomicReference<>();
    private final AtomicReference<GBFeaturesRepository> repository = new AtomicReference<>();
    private volatile RemoteEvalService remoteEvalService;
    private volatile RemoteEvalCache remoteEvalCache;
    private final AtomicBoolean remoteEvalReady = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Boolean>> remoteEvalInit = new AtomicReference<>();
    private final java.util.concurrent.locks.ReentrantLock remoteEvalInitLock =
            new java.util.concurrent.locks.ReentrantLock();
    private final AtomicBoolean clientShutdown = new AtomicBoolean(false);
    private final AtomicReference<Throwable> lastInitializationError = new AtomicReference<>();
    private final AtomicLong lastInitializationErrorAtMillis = new AtomicLong(0);
    private final DiagnosticsProvider diagnosticsProvider;
    private final PluginRegistry pluginRegistry;

    /** Owns all sticky bucket I/O; null when no sticky bucket service is configured. */
    @Nullable
    private final StickyBucketManager stickyBucketManager;
    /** The pool this client created for itself (and must shut down); null when the caller supplied one. */
    @Nullable
    private final ExecutorService ownedAsyncExecutor;

    private static final AtomicLong ASYNC_THREAD_COUNTER = new AtomicLong();

    public GrowthBookClient() {
        this(Options.builder().build());
    }

    public GrowthBookClient(Options opts) {
        this.options = opts == null ? Options.builder().build() : opts;

        // Shared across request threads: run()/subscribe() mutate these on a client
        // that is documented as one-instance-for-all-requests.
        this.assigned = new ConcurrentHashMap<>();
        this.callbacks = new CopyOnWriteArrayList<>();
        this.featureEvaluator = new FeatureEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
        this.diagnosticsProvider = new GrowthBookClientDiagnosticsProvider(this.options, clientStateView());

        if (this.options.isStickyBucketingConfigured()) {
            Executor executor = this.options.getAsyncExecutor();
            if (executor == null) {
                this.ownedAsyncExecutor = newOwnedAsyncExecutor();
                executor = this.ownedAsyncExecutor;
            } else {
                this.ownedAsyncExecutor = null;
            }
            AsyncStickyBucketService asyncService = this.options.getAsyncStickyBucketService() != null
                    ? this.options.getAsyncStickyBucketService()
                    : new SyncOffloadStickyBucketAdapter(this.options.getStickyBucketService(), executor);
            this.stickyBucketManager = new StickyBucketManager(
                    asyncService,
                    this.options.getStickyBucketCacheTtlSeconds(),
                    this.options.getStickyBucketCacheSize());
        } else {
            this.ownedAsyncExecutor = null;
            this.stickyBucketManager = null;
        }

        this.pluginRegistry = new PluginRegistry(this.options.getPlugins());
        this.pluginRegistry.initAll();
    }

    /**
     * Bounded daemon pool for blocking I/O offload — sized for I/O, not CPU.
     * Fixed core==max (a ThreadPoolExecutor never grows past core until its
     * queue is FULL, so a small core with a deep queue serializes I/O) with
     * idle timeout so a quiet client holds zero threads. Default AbortPolicy:
     * CallerRunsPolicy would run blocking store I/O on the request thread at
     * saturation — exactly what this pool exists to prevent — and silently
     * discards tasks after shutdown, leaving futures that never complete.
     */
    private static ExecutorService newOwnedAsyncExecutor() {
        int threads = Math.max(8, 2 * Runtime.getRuntime().availableProcessors());
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                runnable -> {
                    Thread thread = new Thread(runnable, "growthbook-async-" + ASYNC_THREAD_COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private GrowthBookClientDiagnosticsProvider.ClientState clientStateView() {
        return new GrowthBookClientDiagnosticsProvider.ClientState() {
            @Override
            @Nullable
            public GBFeaturesRepository currentRepository() {
                return GrowthBookClient.this.repository.get();
            }

            @Override
            @Nullable
            public Throwable lastInitializationError() {
                return GrowthBookClient.this.lastInitializationError.get();
            }

            @Override
            public long lastInitializationErrorAtMillis() {
                return GrowthBookClient.this.lastInitializationErrorAtMillis.get();
            }

            @Override
            public boolean isRemoteEvalReady() {
                return GrowthBookClient.this.remoteEvalReady.get();
            }

            @Override
            public boolean isShutdown() {
                return GrowthBookClient.this.clientShutdown.get();
            }

            @Override
            public boolean isRemoteEvalCacheConfigured() {
                return GrowthBookClient.this.remoteEvalCache != null;
            }

            @Override
            public int fallbackFeatureCount() {
                GlobalContext context = GrowthBookClient.this.globalContext.get();
                if (context == null || context.getFeatures() == null) {
                    return 0;
                }
                return context.getFeatures().size();
            }
        };
    }

    /**
     * Returns a read-only snapshot of the current SDK state.
     *
     * @return diagnostics snapshot
     */
    public Diagnostics getDiagnostics() {
        return this.diagnosticsProvider.getDiagnostics();
    }

    public boolean initialize() {
        try {
            OptionsValidator.validate(this.options);
        } catch (InvalidOptionsException e) {
            log.error("Failed to initialize growthbook instance", e);
            return false;
        }

        if (this.options.isRemoteEvalEnabled()) {
            try {
                return ensureRemoteEvalReady();
            } catch (RuntimeException e) {
                log.error("Failed to initialize growthbook instance", e);
                return false;
            }
        }

        GBFeaturesRepository repositoryToInitialize = null;
        try {
            repositoryToInitialize = prepareRepositoryForInitialization();
            if (repositoryToInitialize == null) {
                GBFeaturesRepository repositorySnapshot = this.repository.get();
                return repositorySnapshot != null && repositorySnapshot.getInitialized();
            }

            initializeFeaturesRepository(repositoryToInitialize);
            replaceGlobalContextFrom(repositoryToInitialize);

            boolean isReady = this.repository.get() == repositoryToInitialize
                    && repositoryToInitialize.getInitialized();
            if (isReady) {
                this.lastInitializationError.set(null);
                this.lastInitializationErrorAtMillis.set(0);
                log.info("GrowthBookClient initialized repository and registered feature refresh callbacks.");
            }
            return isReady;
        } catch (RuntimeException e) {
            recordInitializationFailure(e);
            clearFailedInitialization(repositoryToInitialize);
            log.error("Failed to initialize growthbook instance", e);
            return false;
        }
    }

    private void recordInitializationFailure(Throwable error) {
        this.lastInitializationError.set(error);
        this.lastInitializationErrorAtMillis.set(System.currentTimeMillis());
    }

    private synchronized GBFeaturesRepository prepareRepositoryForInitialization() {
        if (this.repository.get() != null) {
            return null;
        }

        GBFeaturesRepository repositoryToInitialize = createFeaturesRepository();
        repositoryToInitialize.onFeaturesRefresh(this.options.getFeatureRefreshCallback());
        repositoryToInitialize.onFeaturesRefresh(this.refreshGlobalContext());
        this.repository.set(repositoryToInitialize);
        return repositoryToInitialize;
    }

    private GBFeaturesRepository createFeaturesRepository() {
        GbCacheManager cacheManager = this.options.getCacheManager() != null
                ? this.options.getCacheManager()
                : CacheManagerFactory.create(
                        this.options.getCacheMode(),
                        this.options.getCacheDirectory()
                );

        return GBFeaturesRepository.builder()
                .apiHost(this.options.getApiHost())
                .clientKey(this.options.getClientKey())
                .decryptionKey(this.options.getDecryptionKey())
                .refreshStrategy(this.options.getRefreshStrategy())
                .swrTtlSeconds(this.options.getSwrTtlSeconds())
                .isCacheDisabled(this.options.getIsCacheDisabled() || this.options.getCacheMode() == CacheMode.NONE)
                .cacheManager(cacheManager)
                .backgroundFetchInterval(this.options.getBackgroundFetchInterval())
                .retryPolicy(this.options.getRetryPolicy())
                .requestBodyForRemoteEval(configurePayloadForRemoteEval(this.options))
                .build();
    }

    private void initializeFeaturesRepository(GBFeaturesRepository repositorySnapshot) {
        try {
            repositorySnapshot.initialize();
        } catch (FeatureFetchException e) {
            throw new GrowthBookClientInitializationException(
                    "Failed to initialize features repository", e);
        }
    }

    private void clearFailedInitialization(GBFeaturesRepository failedRepository) {
        if (failedRepository == null || !this.repository.compareAndSet(failedRepository, null)) {
            return;
        }

        this.globalContext.set(null);
        try {
            failedRepository.shutdown();
        } catch (RuntimeException shutdownException) {
            log.warn("Failed to shut down repository after unsuccessful initialization", shutdownException);
        }
    }

    public void setGlobalAttributes(String attributes) {
        this.options.setGlobalAttributes(attributes);
        clearRemoteEvalCache();
    }

    public void setGlobalForceFeatures(Map<String, Object> forceFeatures) {
        this.options.setGlobalForcedFeatureValues(forceFeatures);
        clearRemoteEvalCache();
    }

    public void setGlobalForceVariations(Map<String, Integer> forceVariations) {
        this.options.setGlobalForcedVariationsMap(forceVariations);
        clearRemoteEvalCache();
    }

    @Deprecated
    public void refreshFeature() {
        if (this.options.isRemoteEvalEnabled()) {
            clearRemoteEvalCache();
            return;
        }
        refreshFeatures();
    }

    public void refreshFeatures() {
        refreshFeatures(RefreshMode.DEFAULT);
    }

    /**
     * Refreshes features using the provided refresh mode.
     *
     * @param refreshMode refresh behavior to use
     */
    public void refreshFeatures(RefreshMode refreshMode) {
        GBFeaturesRepository repositorySnapshot = this.repository.get();
        if (repositorySnapshot == null) {
            log.warn("Cannot refresh features before GrowthBookClient is initialized.");
            return;
        }

        try {
            repositorySnapshot.refreshFeatures(refreshMode == null ? RefreshMode.DEFAULT : refreshMode);
        } catch (FeatureFetchException e) {
            log.error("Refreshing features wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    public void refreshForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) {
        if (this.options.isRemoteEvalEnabled()) {
            try {
                RemoteEvalResponse response = getRemoteEvalService().fetch(requestBodyForRemoteEval);
                this.globalContext.set(buildGlobalContext(response.getFeatures(), response.getSavedGroups()));
                clearRemoteEvalCache();
            } catch (FeatureFetchException e) {
                log.error("Refreshing for remote eval wasn't successful. Message is: {}", e.getMessage(), e);
            }
            return;
        }

        GBFeaturesRepository repositorySnapshot = this.repository.get();
        if (repositorySnapshot == null) {
            log.warn("Cannot refresh remote eval before GrowthBookClient is initialized.");
            return;
        }

        try {
            repositorySnapshot.fetchForRemoteEval(requestBodyForRemoteEval);
        } catch (FeatureFetchException e) {
            log.error("Refreshing for remote eval wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    public boolean preloadRemoteEval(UserContext userContext) {
        if (!this.options.isRemoteEvalEnabled()) {
            return false;
        }

        try {
            getRemoteEvalResponse(toUserContextWithMergedAttributes(userContext));
            return true;
        } catch (FeatureFetchException e) {
            log.warn("Unable to preload remote evaluation response", e);
            return false;
        }
    }

    public <ValueType> FeatureResult<ValueType> evalFeature(String key,
                                                            Class<ValueType> valueTypeClass,
                                                            UserContext userContext) {
        return featureEvaluator.evaluateFeature(key, getEvalContext(userContext), valueTypeClass);
    }

    public Boolean isOn(String featureKey, UserContext userContext) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvalContext(userContext), Object.class).isOn();
    }

    public Boolean isOff(String featureKey, UserContext userContext) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvalContext(userContext), Object.class).isOff();
    }

    public <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue,
                                                 Class<ValueType> gsonDeserializableClass,
                                                 UserContext userContext) {
        try {
            Object maybeValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvalContext(userContext), gsonDeserializableClass).getValue();

            if (maybeValue == null) {
                return defaultValue;
            }

            String stringValue = GrowthBookJsonUtils.getInstance().gson.toJson(maybeValue);

            return GrowthBookJsonUtils.getInstance().gson.fromJson(stringValue, gsonDeserializableClass);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return defaultValue;
        }
    }

    /**
     * Evaluate a feature for a user using a type-safe {@link FeatureKey} instead of a raw string key.
     *
     * <p>As with {@link #evalFeature(String, Class, UserContext)}, the returned result's
     * {@link FeatureResult#getValue()} is the raw evaluated value (a boxed primitive, or a
     * {@code Map}/{@code List} for object and array features); it is <em>not</em> deserialized
     * into the key's value type. To obtain a deserialized instance of a complex type, use
     * {@link #getFeatureValue(FeatureKey, Object, UserContext)}.
     *
     * @param featureKey  typed feature key, e.g. {@code Features.NEW_HOME}
     * @param userContext user context
     * @param <T>         feature value type carried by the key
     * @return the feature result
     */
    public <T> FeatureResult<T> getFeature(FeatureKey<T> featureKey, UserContext userContext) {
        return evalFeature(featureKey.getKey(), featureKey.getValueType(), userContext);
    }

    /**
     * Checks whether the feature identified by the typed key evaluates to on for a user.
     *
     * @param featureKey  typed feature key
     * @param userContext user context
     * @return true when the feature is on
     */
    public Boolean isOn(FeatureKey<?> featureKey, UserContext userContext) {
        return isOn(featureKey.getKey(), userContext);
    }

    /**
     * Checks whether the feature identified by the typed key evaluates to off for a user.
     *
     * @param featureKey  typed feature key
     * @param userContext user context
     * @return true when the feature is off
     */
    public Boolean isOff(FeatureKey<?> featureKey, UserContext userContext) {
        return isOff(featureKey.getKey(), userContext);
    }

    /**
     * Get a feature value using a typed key, inferring the deserialization class from the key.
     *
     * @param featureKey   typed feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param userContext  user context
     * @param <T>          feature value type carried by the key
     * @return the found value or defaultValue
     */
    public <T> T getFeatureValue(FeatureKey<T> featureKey, T defaultValue, UserContext userContext) {
        return getFeatureValue(featureKey.getKey(), defaultValue, featureKey.getValueType(), userContext);
    }

    /**
     * Get a boolean feature value, defaulting to {@code false} when missing or falsy.
     *
     * @param featureKey  typed boolean feature key
     * @param userContext user context
     * @return the found value or {@code false}
     */
    public Boolean getBooleanFeature(FeatureKey<Boolean> featureKey, UserContext userContext) {
        return getFeatureValue(featureKey, false, userContext);
    }

    /**
     * Get a boolean feature value.
     *
     * @param featureKey   typed boolean feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param userContext  user context
     * @return the found value or defaultValue
     */
    public Boolean getBooleanFeature(FeatureKey<Boolean> featureKey, Boolean defaultValue, UserContext userContext) {
        return getFeatureValue(featureKey, defaultValue, userContext);
    }

    /**
     * Get a string feature value.
     *
     * @param featureKey   typed string feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param userContext  user context
     * @return the found value or defaultValue
     */
    public String getStringFeature(FeatureKey<String> featureKey, String defaultValue, UserContext userContext) {
        return getFeatureValue(featureKey, defaultValue, userContext);
    }

    /**
     * Get an integer feature value.
     *
     * @param featureKey   typed integer feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param userContext  user context
     * @return the found value or defaultValue
     */
    public Integer getIntegerFeature(FeatureKey<Integer> featureKey, Integer defaultValue, UserContext userContext) {
        return getFeatureValue(featureKey, defaultValue, userContext);
    }

    /**
     * Get a double feature value.
     *
     * @param featureKey   typed double feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param userContext  user context
     * @return the found value or defaultValue
     */
    public Double getDoubleFeature(FeatureKey<Double> featureKey, Double defaultValue, UserContext userContext) {
        return getFeatureValue(featureKey, defaultValue, userContext);
    }

    /**
     * Get a float feature value.
     *
     * @param featureKey   typed float feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param userContext  user context
     * @return the found value or defaultValue
     */
    public Float getFloatFeature(FeatureKey<Float> featureKey, Float defaultValue, UserContext userContext) {
        return getFeatureValue(featureKey, defaultValue, userContext);
    }

    public <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment, UserContext userContext) {
        ExperimentResult<ValueType> result = experimentEvaluatorEvaluator
                .evaluateExperiment(experiment, getEvalContext(userContext), null);

        fireSubscriptions(experiment, result);

        return result;
    }

    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }

    public void shutdown() {
        // CAS instead of synchronized: shutdown blocks for seconds (sticky flush,
        // plugin close) and a monitor held that long pins virtual-thread carriers
        // and stalls every synchronized method on this client. CAS also makes
        // shutdown idempotent — a second caller returns immediately.
        if (!this.clientShutdown.compareAndSet(false, true)) {
            return;
        }
        // Drain sticky bucket saves FIRST, while the offload executor is fully
        // alive — shutting the executor down before the flush would discard
        // trailing saves and leave the flush waiting on futures nobody completes.
        if (this.stickyBucketManager != null) {
            this.stickyBucketManager.startDraining();
            this.stickyBucketManager.close(5_000);
        }
        if (this.ownedAsyncExecutor != null) {
            this.ownedAsyncExecutor.shutdown();
            try {
                if (!this.ownedAsyncExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    this.ownedAsyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.ownedAsyncExecutor.shutdownNow();
            }
        }
        GBFeaturesRepository repositorySnapshot = this.repository.getAndSet(null);
        this.globalContext.set(null);
        if (repositorySnapshot != null) {
            repositorySnapshot.shutdown();
            log.info("Repository shut down");
        }
        if (this.remoteEvalCache != null) {
            this.remoteEvalCache.shutdown();
        }
        if (this.remoteEvalService != null) {
            this.remoteEvalService.close();
        }
        // Flush registered plugins (including the built-in tracking plugin) so
        // any buffered events are sent before the client is discarded.
        this.pluginRegistry.closeAll();
    }

    private boolean ensureRemoteEvalReady() {
        if (this.remoteEvalReady.get()) {
            return true;
        }
        // One-time-init future instead of synchronized(this): the initialization
        // performs blocking HTTP (SSE invalidation setup), and a monitor held
        // across network I/O pins virtual-thread carriers and blocks every other
        // synchronized member. Exactly one caller initializes; concurrent callers
        // wait on the future. A failed attempt resets so the next call retries
        // (preserving the previous retry-on-every-call semantics).
        while (true) {
            if (this.remoteEvalReady.get()) {
                return true;
            }
            CompletableFuture<Boolean> inflight = this.remoteEvalInit.get();
            if (inflight == null) {
                CompletableFuture<Boolean> created = new CompletableFuture<>();
                if (!this.remoteEvalInit.compareAndSet(null, created)) {
                    continue;
                }
                try {
                    RemoteEvalOptionsValidator.validate(this.options);
                    getRemoteEvalService();
                    getRemoteEvalCache();
                    initializeRemoteEvalSseInvalidationIfNeeded();
                    this.globalContext.compareAndSet(null, buildGlobalContext(Collections.emptyMap(), new JsonObject()));
                    this.remoteEvalReady.set(true);
                    created.complete(true);
                    return true;
                } catch (RuntimeException e) {
                    this.remoteEvalInit.compareAndSet(created, null);
                    created.completeExceptionally(e);
                    throw e;
                }
            }
            try {
                return inflight.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for remote eval initialization", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    private void initializeRemoteEvalSseInvalidationIfNeeded() {
        if (this.options.getRefreshStrategy() != FeatureRefreshStrategy.SERVER_SENT_EVENTS || this.repository.get() != null) {
            return;
        }

        GBFeaturesRepository sseRepository = GBFeaturesRepository.builder()
                .apiHost(this.options.getApiHost())
                .clientKey(this.options.getClientKey())
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .isCacheDisabled(true)
                .build();
        sseRepository.onFeaturesRefresh(new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                clearRemoteEvalCache();
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Unable to receive remote evaluation invalidation event", throwable);
            }
        });

        if (!this.repository.compareAndSet(null, sseRepository)) {
            return;
        }

        try {
            sseRepository.initialize();
        } catch (FeatureFetchException e) {
            log.warn("Remote evaluation SSE invalidation could not be initialized", e);
        }
    }

    private <ValueType> void fireSubscriptions(Experiment<ValueType> experiment, ExperimentResult<ValueType> result) {
        // ConcurrentHashMap rejects null keys (the previous HashMap tolerated them);
        // a key-less experiment still dedupes, under one shared sentinel entry.
        String key = experiment.getKey() != null ? experiment.getKey() : "";
        // If assigned variation has changed, fire subscriptions. The change check and
        // the publish must be one atomic step or two concurrent run() calls can both
        // observe the stale value and double-fire. Callbacks run outside compute():
        // user code must never execute inside a ConcurrentHashMap bin lock.
        boolean[] changed = {false};
        this.assigned.compute(key, (k, prev) -> {
            if (prev == null
                    || !Objects.equals(prev.getInExperiment(), result.getInExperiment())
                    || !Objects.equals(prev.getVariationId(), result.getVariationId())) {
                changed[0] = true;
                return new AssignedExperiment(
                        experiment.getKey(),
                        result.getInExperiment(),
                        result.getVariationId()
                );
            }
            return prev;
        });

        if (changed[0]) {
            for (ExperimentRunCallback cb : this.callbacks) {
                try {
                    cb.onRun(experiment, result);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }
    }

    private FeatureRefreshCallback refreshGlobalContext() {
        return new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                GBFeaturesRepository currentRepository = GrowthBookClient.this.repository.get();
                if (currentRepository == null) {
                    log.debug("Skipping global context refresh because the features repository is not initialized.");
                    return;
                }

                replaceGlobalContextFrom(currentRepository);
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Unable to refresh global context with latest features", throwable);
            }
        };
    }

    private synchronized void replaceGlobalContextFrom(GBFeaturesRepository refreshedRepository) {
        if (this.repository.get() != refreshedRepository) {
            log.debug("Skipping global context refresh from a stale features repository.");
            return;
        }

        this.globalContext.set(buildGlobalContext(refreshedRepository));
    }

    private GlobalContext buildGlobalContext(GBFeaturesRepository sourceRepository) {
        // Read the payload as ONE snapshot: two separate getter calls could pair
        // new features with old saved groups if a refresh lands in between.
        FeatureSnapshot featureSnapshot = sourceRepository.getFeatureSnapshot();
        return GlobalContext.builder()
                .features(featureSnapshot.getParsedFeatures())
                .savedGroups(featureSnapshot.getParsedSavedGroups())
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }

    private EvaluationContext getEvalContext(UserContext userContext) {
        UserContext updatedUserContext = toUserContextWithMergedAttributes(userContext);
        if (this.options.isRemoteEvalEnabled()) {
            return getRemoteEvalContext(updatedUserContext);
        }
        EvaluationContext evaluationContext = withPluginRegistry(
                new EvaluationContext(getLocalGlobalContext(), updatedUserContext, new EvaluationContext.StackContext(), this.options));
        if (this.stickyBucketManager != null) {
            // Fire-and-forget persistence (JS/Python parity): evaluation never waits
            // on the store; the manager merges, serializes per key, and tracks the
            // save for flushStickyBucketSaves()/shutdown().
            evaluationContext.setStickyBucketDocWriter(this.stickyBucketManager::recordAndSave);
        }
        return evaluationContext;
    }

    /**
     * Waits for every pending sticky bucket save to be persisted. Evaluation
     * writes assignments fire-and-forget; long-running services never need this,
     * but short-lived processes (serverless functions, batch jobs) should await
     * it — or call {@link #shutdown()}, which flushes automatically — before
     * exiting to guarantee durability.
     *
     * @return a future completing when all pending saves have been attempted;
     *         already complete when no sticky bucket service is configured
     */
    public CompletableFuture<Void> flushStickyBucketSaves() {
        if (this.stickyBucketManager == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.stickyBucketManager.flush();
    }

    /**
     * Prefetch sticky bucket assignments for a user context, so subsequent
     * evaluations with that same context perform no sticky bucket I/O at all
     * (the JavaScript SDK's {@code applyStickyBuckets} pattern: fetch once per
     * request, evaluate any number of flags). The supplied context's
     * {@code stickyBucketAssignmentDocs} is populated in place and the same
     * instance is returned.
     *
     * <p>The returned future is caller-owned: cancelling it detaches this
     * caller's stage only and never aborts the underlying store lookup or
     * affects concurrent callers coalesced onto the same fetch.
     *
     * @param userContext the request-scoped user context to prefetch for
     * @return a future completing with the same context, docs populated
     */
    public CompletableFuture<UserContext> prefetchStickyBuckets(UserContext userContext) {
        UserContext context = userContext == null ? UserContext.builder().build() : userContext;
        if (this.stickyBucketManager == null || context.getStickyBucketAssignmentDocs() != null) {
            return CompletableFuture.completedFuture(context);
        }
        UserContext merged = toUserContextWithMergedAttributesOnly(context);
        Map<String, String> attributes = stickyIdentifierAttributesFor(merged.getAttributes());
        return this.stickyBucketManager.fetchAssignments(attributes)
                .thenApply(docs -> {
                    context.setStickyBucketAssignmentDocs(docs);
                    return context;
                });
    }

    private EvaluationContext getRemoteEvalContext(UserContext userContext) {
        try {
            RemoteEvalResponse response = getRemoteEvalResponse(userContext);
            GlobalContext remoteGlobalContext = buildGlobalContext(response.getFeatures(), response.getSavedGroups());
            return withPluginRegistry(new EvaluationContext(remoteGlobalContext, userContext, new EvaluationContext.StackContext(), this.options));
        } catch (FeatureFetchException e) {
            log.warn("Remote evaluation request failed. Falling back to local feature context.", e);
            return withPluginRegistry(new EvaluationContext(getLocalGlobalContext(), userContext, new EvaluationContext.StackContext(), this.options));
        }
    }

    /** Attaches this client's own plugin registry so events never route through another client's plugins. */
    private EvaluationContext withPluginRegistry(EvaluationContext context) {
        context.setPluginRegistry(this.pluginRegistry);
        return context;
    }

    /** Attribute merge only — no sticky bucket I/O. */
    private UserContext toUserContextWithMergedAttributesOnly(UserContext userContext) {
        UserContext currentUserContext = userContext == null ? UserContext.builder().build() : userContext;
        JsonObject merged = new JsonObject();
        if (this.options.getGlobalAttributes() != null) {
            merged = GrowthBookJsonUtils.getInstance().gson.fromJson(this.options.getGlobalAttributes(), JsonObject.class);
            if (merged == null) merged = new JsonObject();
        }
        JsonObject userAttrs = currentUserContext.getAttributes();
        if (userAttrs != null) {
            for (Map.Entry<String, JsonElement> e : userAttrs.entrySet()) {
                merged.add(e.getKey(), e.getValue());
            }
        }
        return currentUserContext.withAttributes(merged);
    }

    private UserContext toUserContextWithMergedAttributes(UserContext userContext) {
        UserContext updatedUserContext = toUserContextWithMergedAttributesOnly(userContext);
        JsonObject merged = updatedUserContext.getAttributes();

        // If a sticky bucket service is configured and the caller hasn't preloaded
        // docs (directly or via prefetchStickyBuckets), fetch them now — scoped to
        // the identifier attributes actually used by experiments, coalesced with
        // concurrent fetches for the same user, and overlaid with this process's
        // own writes. The docs map is fresh per evaluation: the evaluator mutates
        // it in place and it must never be shared between evaluations.
        if (this.stickyBucketManager != null
                && updatedUserContext.getStickyBucketAssignmentDocs() == null) {
            Map<String, String> attrStrings = stickyIdentifierAttributesFor(merged);
            updatedUserContext.setStickyBucketAssignmentDocs(fetchStickyDocsBlocking(attrStrings));
        }

        return updatedUserContext;
    }

    /**
     * The identifier attribute name → value pairs to fetch sticky documents for:
     * {@code Options.stickyBucketIdentifierAttributes} when configured, else
     * derived from the current feature snapshot's experiment rules (memoized on
     * the {@link GlobalContext}, which is rebuilt on every feature refresh).
     */
    private Map<String, String> stickyIdentifierAttributesFor(JsonObject mergedAttributes) {
        Set<String> identifiers = resolveStickyIdentifierAttributes();
        Map<String, String> attributes = new HashMap<>(Math.max(4, identifiers.size() * 2));
        for (String name : identifiers) {
            JsonElement value = mergedAttributes.get(name);
            if (value != null && value.isJsonPrimitive()) {
                attributes.put(name, value.getAsString());
            }
        }
        return attributes;
    }

    private Set<String> resolveStickyIdentifierAttributes() {
        List<String> configured = this.options.getStickyBucketIdentifierAttributes();
        if (configured != null && !configured.isEmpty()) {
            return new HashSet<>(configured);
        }
        GlobalContext currentGlobalContext = getLocalGlobalContext();
        Set<String> derived = currentGlobalContext.getDerivedStickyIdentifierAttributes();
        if (derived == null) {
            derived = deriveStickyIdentifierAttributes(currentGlobalContext.getFeatures());
            // Benign race: derivation is idempotent for one snapshot.
            currentGlobalContext.setDerivedStickyIdentifierAttributes(derived);
        }
        return derived;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<String> deriveStickyIdentifierAttributes(
            @Nullable Map<String, growthbook.sdk.java.model.Feature<?>> features) {
        Set<String> attributes = new HashSet<>();
        if (features == null) {
            return attributes;
        }
        for (growthbook.sdk.java.model.Feature<?> feature : features.values()) {
            if (feature == null || feature.getRules() == null) {
                continue;
            }
            for (growthbook.sdk.java.model.FeatureRule rule
                    : (List<growthbook.sdk.java.model.FeatureRule>) (List) feature.getRules()) {
                if (rule.getVariations() != null && !rule.getVariations().isEmpty()) {
                    attributes.add(rule.getHashAttribute() != null ? rule.getHashAttribute() : "id");
                    if (rule.getFallbackAttribute() != null) {
                        attributes.add(rule.getFallbackAttribute());
                    }
                }
            }
        }
        return attributes;
    }

    /**
     * Sync bridge into the manager's coalesced fetch. Uses {@code get()} rather
     * than {@code join()}: Java 8's {@code join()} is uninterruptible, and a
     * hung store must not make request threads immune to interruption. Fetch
     * failures propagate (JS/Python parity — the SDK never silently evaluates
     * without sticky data when a service is configured).
     */
    private Map<String, StickyAssignmentsDocument> fetchStickyDocsBlocking(Map<String, String> attributes) {
        try {
            return this.stickyBucketManager.fetchAssignments(attributes).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching sticky bucket assignments", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Sticky bucket assignment fetch failed", cause);
        }
    }

    private RemoteEvalResponse getRemoteEvalResponse(UserContext userContext) throws FeatureFetchException {
        ensureRemoteEvalReady();

        Map<String, Integer> forcedVariations = mergeForcedVariations(userContext);
        Map<String, Object> forcedFeatures = mergeForcedFeatures(userContext);
        String url = userContext.getUrl() == null ? this.options.getUrl() : userContext.getUrl();
        url = RemoteEvalRequestBuilder.normalizeUrl(url);
        String cacheKey = RemoteEvalCacheKey.fromContext(
                this.options.getApiHost(),
                this.options.getClientKey(),
                userContext.getAttributes(),
                forcedVariations,
                forcedFeatures,
                url,
                this.options.getCacheKeyAttributes()
        );

        RequestBodyForRemoteEval requestBody = RemoteEvalRequestBuilder.build(
                userContext.getAttributes(),
                forcedFeatures,
                forcedVariations,
                url
        );
        return getRemoteEvalCache().get(cacheKey, requestBody);
    }

    // Double-checked on the volatile fields with a ReentrantLock slow path:
    // getRemoteEvalCache() sits on the remote-eval evaluation hot path, and a
    // synchronized method there contends every eval and pins virtual threads.
    private RemoteEvalService getRemoteEvalService() {
        RemoteEvalService service = this.remoteEvalService;
        if (service != null) {
            return service;
        }
        this.remoteEvalInitLock.lock();
        try {
            if (this.remoteEvalService == null) {
                this.remoteEvalService = new RemoteEvalService(this.options.getApiHost(), this.options.getClientKey());
            }
            return this.remoteEvalService;
        } finally {
            this.remoteEvalInitLock.unlock();
        }
    }

    private RemoteEvalCache getRemoteEvalCache() {
        RemoteEvalCache cache = this.remoteEvalCache;
        if (cache != null) {
            return cache;
        }
        this.remoteEvalInitLock.lock();
        try {
            if (this.remoteEvalCache == null) {
                this.remoteEvalCache = new RemoteEvalCache(
                        getRemoteEvalService(),
                        RemoteEvalRequestBuilder.normalizeCacheSize(this.options.getRemoteEvalCacheSize()),
                        secondsToDuration(this.options.getSwrTtlSeconds() == null
                                ? SDKConstants.DEFAULT_SWR_TTL_SECONDS
                                : this.options.getSwrTtlSeconds()),
                        secondsToDuration(this.options.getRemoteEvalCacheTtlSeconds())
                );
            }
            return this.remoteEvalCache;
        } finally {
            this.remoteEvalInitLock.unlock();
        }
    }

    private static Duration secondsToDuration(@Nullable Integer seconds) {
        return seconds == null ? null : Duration.ofSeconds(seconds);
    }

    private void clearRemoteEvalCache() {
        RemoteEvalCache cache = this.remoteEvalCache;
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    private GlobalContext getLocalGlobalContext() {
        this.globalContext.compareAndSet(null, buildGlobalContext(Collections.emptyMap(), new JsonObject()));
        return this.globalContext.get();
    }

    private GlobalContext buildGlobalContext(Map<String, growthbook.sdk.java.model.Feature<?>> features, JsonObject savedGroups) {
        return GlobalContext.builder()
                .features(features == null ? Collections.emptyMap() : features)
                .savedGroups(savedGroups == null ? new JsonObject() : savedGroups)
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }

    private Map<String, Integer> mergeForcedVariations(UserContext userContext) {
        Map<String, Integer> forcedVariations = new HashMap<>();
        if (this.options.getGlobalForcedVariationsMap() != null) {
            forcedVariations.putAll(this.options.getGlobalForcedVariationsMap());
        }
        if (userContext.getForcedVariationsMap() != null) {
            forcedVariations.putAll(userContext.getForcedVariationsMap());
        }
        return forcedVariations;
    }

    private Map<String, Object> mergeForcedFeatures(UserContext userContext) {
        Map<String, Object> forcedFeatures = new HashMap<>();
        if (this.options.getGlobalForcedFeatureValues() != null) {
            forcedFeatures.putAll(this.options.getGlobalForcedFeatureValues());
        }
        if (userContext.getForcedFeatureValues() != null) {
            forcedFeatures.putAll(userContext.getForcedFeatureValues());
        }
        return forcedFeatures;
    }

    private RequestBodyForRemoteEval configurePayloadForRemoteEval(Options options) {
        return RemoteEvalRequestBuilder.build(
                options.getGlobalAttributes(),
                options.getGlobalForcedFeatureValues(),
                options.getGlobalForcedVariationsMap(),
                options.getUrl()
        );
    }
}
