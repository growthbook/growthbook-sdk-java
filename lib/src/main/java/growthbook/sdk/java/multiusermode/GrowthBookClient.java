package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.diagnostics.model.Diagnostics;
import growthbook.sdk.java.diagnostics.provider.DiagnosticsProvider;
import growthbook.sdk.java.diagnostics.provider.GrowthBookClientDiagnosticsProvider;
import growthbook.sdk.java.evaluators.PayloadReducer;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureRule;
import growthbook.sdk.java.model.ReducePayloadOptions;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.OptionsValidator;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.internal.ClientCore;
import growthbook.sdk.java.multiusermode.internal.FeatureRefreshListenerRegistry;
import growthbook.sdk.java.multiusermode.internal.FeatureValues;
import growthbook.sdk.java.multiusermode.internal.FeatureRepositoryProvider;
import growthbook.sdk.java.multiusermode.internal.GlobalContextManager;
import growthbook.sdk.java.multiusermode.internal.ManagedListenerExecutor;
import growthbook.sdk.java.multiusermode.internal.RemoteEvalCoordinator;
import growthbook.sdk.java.multiusermode.internal.StickyAttributeValues;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.StickyBucketDocWriter;
import growthbook.sdk.java.stickyBucketing.SyncOffloadStickyBucketAdapter;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Multi-user GrowthBook SDK facade.
 *
 * <p>The client owns one feature repository per instance in local mode. In remote-eval mode,
 * feature evaluation is fetched through the remote eval service and cached per user context.
 */
@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final ClientCore core;
    private final DiagnosticsProvider diagnosticsProvider;
    private final ManagedListenerExecutor listenerExecutor;
    private final GlobalContextManager globalContextManager;
    private final RemoteEvalCoordinator remoteEvalCoordinator;
    private final FeatureRepositoryProvider featureRepositoryProvider;
    private final FeatureRefreshListenerRegistry featureRefreshListeners;

    private final AtomicBoolean clientShutdown = new AtomicBoolean(false);
    private final AtomicLong lastInitializationErrorAtMillis = new AtomicLong(0);
    private final AtomicReference<Throwable> lastInitializationError = new AtomicReference<>();

    /** Owns all sticky bucket I/O; null when no sticky bucket service is configured. */
    @Nullable
    private final StickyBucketManager stickyBucketManager;
    /** The pool this client created for itself (and must shut down); null when the caller supplied one. */
    @Nullable
    private final ExecutorService ownedAsyncExecutor;
    /** Resolved offload executor for async APIs; the owned pool unless the caller supplied one. */
    private final Executor asyncExecutor;

    /**
     * Futures returned by the asynchronous methods that are still pending, each with what
     * {@link #shutdown()} completes it with, so no caller is left waiting on a discarded task.
     */
    private final Map<CompletableFuture<?>, Runnable> pendingAsync = new ConcurrentHashMap<>();

    private static final AtomicLong ASYNC_THREAD_COUNTER = new AtomicLong();

    /**
     * Creates a client with default options.
     */
    public GrowthBookClient() {
        this(Options.builder().build());
    }

    /**
     * Creates a client with the supplied options.
     *
     * @param opts client options; null falls back to default options
     */
    public GrowthBookClient(Options opts) {
        this.options = opts == null ? Options.builder().build() : opts;

        this.globalContextManager = new GlobalContextManager(this.options);
        this.listenerExecutor = ManagedListenerExecutor.resolve(this.options.getFeatureRefreshListenerExecutor());
        this.featureRefreshListeners = new FeatureRefreshListenerRegistry(listenerExecutor.executor());
        this.remoteEvalCoordinator = new RemoteEvalCoordinator(this.options, this.featureRefreshListeners);
        this.featureRepositoryProvider = new FeatureRepositoryProvider(
                this.options,
                this::registerRefreshHandlers,
                this.globalContextManager::initialize
        );
        this.diagnosticsProvider = new GrowthBookClientDiagnosticsProvider(this.options, clientStateView());

        Executor configuredExecutor = this.options.getAsyncExecutor();
        if (configuredExecutor == null) {
            this.ownedAsyncExecutor = newOwnedAsyncExecutor();
            this.asyncExecutor = this.ownedAsyncExecutor;
        } else {
            this.ownedAsyncExecutor = null;
            this.asyncExecutor = configuredExecutor;
        }

        if (this.options.isStickyBucketingConfigured()) {
            AsyncStickyBucketService asyncService = this.options.getAsyncStickyBucketService();
            AsyncStickyBucketService callerThreadService = asyncService;
            if (asyncService == null) {
                // A blocking store can only be made non-blocking by spending a thread on it. The
                // synchronous evaluation API already has one (the caller's), so only the
                // asynchronous entry points go through the executor.
                asyncService = new SyncOffloadStickyBucketAdapter(
                        this.options.getStickyBucketService(), this.asyncExecutor);
                callerThreadService = new CallerThreadStickyBucketAdapter(this.options.getStickyBucketService());
            }
            this.stickyBucketManager = new StickyBucketManager(
                    asyncService,
                    callerThreadService,
                    this.options.getStickyBucketCacheTtlSeconds(),
                    this.options.getStickyBucketCacheSize(),
                    this.options.getStickyBucketTimeout().toMillis());
        } else {
            this.stickyBucketManager = null;
        }

        this.core = new ClientCore(this.options.getPlugins(), new ExperimentTracker(10_000));
        this.core.startPlugins();
    }

    /**
     * Bounded daemon pool for the asynchronous entry points' blocking I/O offload — sized for I/O,
     * not CPU. Fixed core==max with idle timeout so a quiet client holds zero threads. Default
     * AbortPolicy: a saturated pool rejects the task instead of running blocking store I/O on the
     * thread that asked not to be blocked. The synchronous evaluation API never uses this pool.
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
                return GrowthBookClient.this.currentRepository();
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
                return GrowthBookClient.this.remoteEvalCoordinator.isReady();
            }

            @Override
            public boolean isShutdown() {
                return GrowthBookClient.this.clientShutdown.get();
            }

            @Override
            public boolean isRemoteEvalCacheConfigured() {
                return GrowthBookClient.this.remoteEvalCoordinator.isCacheConfigured();
            }

            @Override
            public int fallbackFeatureCount() {
                return GrowthBookClient.this.options.isRemoteEvalEnabled()
                        ? GrowthBookClient.this.remoteEvalCoordinator.fallbackFeatureCount()
                        : GrowthBookClient.this.globalContextManager.featureCount();
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

    /**
     * Initializes the feature repository or remote-eval service.
     *
     * @return true when the client is ready for evaluation
     */
    public boolean initialize() {
        try {
            OptionsValidator.validate(this.options);
        } catch (InvalidOptionsException e) {
            recordInitializationFailure(e);
            log.error("Failed to initialize growthbook instance", e);
            return false;
        }

        try {
            boolean ready;
            if (this.options.isRemoteEvalEnabled()) {
                ready = this.remoteEvalCoordinator.initialize();
            } else {
                GBFeaturesRepository repository = featureRepositoryProvider.initialize();
                ready = repository != null && repository.getInitialized();
                if (!ready) {
                    // FeatureRepositoryProvider.initialize() swallows the underlying fetch failure;
                    // surface it so diagnostics can report the initialization error.
                    Throwable cause = featureRepositoryProvider.lastInitializationError();
                    if (cause != null) {
                        recordInitializationFailure(cause);
                    }
                }
            }

            if (ready) {
                this.lastInitializationError.set(null);
                this.lastInitializationErrorAtMillis.set(0);
            }
            return ready;
        } catch (RuntimeException e) {
            recordInitializationFailure(e);
            log.error("Failed to initialize growthbook instance", e);
            return false;
        }
    }

    /**
     * Async twin of {@link #initialize()}: loads features without blocking the caller. It runs
     * {@link #initialize()} on the client's executor and holds one of its threads for the fetch.
     * The returned future never completes exceptionally: it completes with {@code false} on invalid
     * options, a failed fetch, an executor that rejects the task, or a {@link #shutdown()} that
     * comes first.
     *
     * @return a future completing with whether the client is ready for evaluation
     */
    public CompletableFuture<Boolean> initializeAsync() {
        // A task still queued when shutdown() runs must not start a repository on a closed client.
        Supplier<Boolean> initializeUnlessShutDown = () -> !clientShutdown.get() && initialize();
        CompletableFuture<Boolean> future = supplyOnExecutor(initializeUnlessShutDown).exceptionally(error -> {
            log.error("Failed to initialize growthbook instance", error);
            return false;
        });
        return completeOnShutdown(future, () -> future.complete(false));
    }

    /**
     * {@code supplyAsync} on the client's executor that reports an executor which will not take the
     * task (typically a {@link RejectedExecutionException}) through the returned future instead of
     * throwing, so an asynchronous method never fails on the calling thread.
     */
    private <T> CompletableFuture<T> supplyOnExecutor(Supplier<T> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier, this.asyncExecutor);
        } catch (RuntimeException e) {
            return failedFuture(e);
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(error);
        return failed;
    }

    /**
     * Registers a pending future with what {@link #shutdown()} must complete it with. A future
     * registered after shutdown has already swept is completed here, so the race between the two
     * cannot leave it pending.
     */
    private <T> CompletableFuture<T> completeOnShutdown(CompletableFuture<T> future, Runnable onShutdown) {
        if (future.isDone()) {
            return future;
        }
        pendingAsync.put(future, onShutdown);
        future.whenComplete((result, error) -> pendingAsync.remove(future));
        if (clientShutdown.get()) {
            onShutdown.run();
        }
        return future;
    }

    /** Test seam: how many async futures are registered for {@link #shutdown()} right now. */
    int pendingAsyncCount() {
        return pendingAsync.size();
    }

    /** An async evaluation that {@link #shutdown()} completes exceptionally if it is still pending. */
    private <T> CompletableFuture<T> trackAsyncEvaluation(CompletableFuture<T> future) {
        return completeOnShutdown(future, () -> future.completeExceptionally(shutDownError()));
    }

    private static IllegalStateException shutDownError() {
        return new IllegalStateException("GrowthBookClient has been shut down");
    }

    private void recordInitializationFailure(Throwable error) {
        this.lastInitializationError.set(error);
        this.lastInitializationErrorAtMillis.set(System.currentTimeMillis());
    }

    /**
     * Replaces global attributes used for future evaluations.
     *
     * @param attributes JSON string containing global attributes
     */
    public void setGlobalAttributes(String attributes) {
        this.options.setGlobalAttributes(attributes);
        this.remoteEvalCoordinator.invalidateCache();
    }

    /**
     * Replaces globally forced feature values used for future evaluations.
     *
     * @param forceFeatures feature key to forced value map
     */
    public void setGlobalForceFeatures(Map<String, Object> forceFeatures) {
        this.options.setGlobalForcedFeatureValues(forceFeatures);
        this.remoteEvalCoordinator.invalidateCache();
    }

    /**
     * Replaces globally forced variations used for future experiment evaluations.
     *
     * @param forceVariations experiment key to forced variation index map
     */
    public void setGlobalForceVariations(Map<String, Integer> forceVariations) {
        this.options.setGlobalForcedVariationsMap(forceVariations);
        this.remoteEvalCoordinator.invalidateCache();
    }

    /**
     * Fetches the latest feature definitions using the default refresh path.
     *
     * @deprecated Use {@link #refreshFeatures()} or {@link #refreshFeatures(RefreshMode)}.
     */
    @Deprecated
    public void refreshFeature() {
        refreshFeatures();
    }

    /**
     * Refreshes feature definitions using the default refresh behavior.
     */
    public void refreshFeatures() {
        refreshFeatures(RefreshMode.DEFAULT);
    }

    /**
     * Refreshes feature definitions using the provided refresh mode.
     *
     * <p>Blocks until the refresh completes, so an evaluation made right after this call sees the
     * refreshed definitions. Use {@link GBFeaturesRepository#requestFeatureRefresh(RefreshMode)}
     * directly when a fire-and-forget refresh is wanted instead.
     *
     * <p>In remote-eval mode this invalidates the remote-eval response cache. The next evaluation
     * fetches a fresh remote response for that user context.
     *
     * @param refreshMode refresh behavior to use
     */
    public void refreshFeatures(RefreshMode refreshMode) {
        if (this.options.isRemoteEvalEnabled()) {
            this.remoteEvalCoordinator.invalidateCache();
            return;
        }

        GBFeaturesRepository repositorySnapshot = currentRepository();
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

    /**
     * Refreshes feature definitions using remote evaluation payload.
     *
     * @param requestBodyForRemoteEval remote evaluation request payload
     */
    public void refreshForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) {
        try {
            if (this.options.isRemoteEvalEnabled()) {
                this.remoteEvalCoordinator.refresh(requestBodyForRemoteEval);
                return;
            }

            GBFeaturesRepository repositorySnapshot = currentRepository();
            if (repositorySnapshot == null) {
                log.warn("Cannot refresh remote evaluation features before GrowthBookClient is initialized.");
                return;
            }
            repositorySnapshot.fetchForRemoteEval(requestBodyForRemoteEval);
        } catch (FeatureFetchException e) {
            log.error("Refreshing for remote eval wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    public boolean preloadRemoteEval(UserContext userContext) {
        if (!this.options.isRemoteEvalEnabled()) {
            return false;
        }
        return this.remoteEvalCoordinator.preload(userContext);
    }

    /**
     * Evaluates a feature for a user.
     *
     * @param key feature key
     * @param valueTypeClass expected value class
     * @param userContext user context
     * @param <T> feature value type
     * @return feature evaluation result
     */
    public <T> FeatureResult<T> evalFeature(String key,
                                            Class<T> valueTypeClass,
                                            UserContext userContext) {
        return core.evalFeature(key, valueTypeClass, getEvalContext(userContext));
    }

    /**
     * Evaluates a batch of features for the same user using a single shared
     * {@link EvaluationContext}. Attribute merging, global-context references and any
     * sticky-bucket preload happen once for the whole batch instead of once per feature,
     * so memory stays O(size(context)) rather than O(features × size(context)).
     *
     * @param featureKeys    the feature keys to evaluate
     * @param valueTypeClass the expected value type (typically {@code Object.class} for mixed types)
     * @param userContext    the user context, processed once
     * @param <T>    the result value type
     * @return a map from feature key to its {@link FeatureResult}
     */
    public <T> Map<String, FeatureResult<T>> evalFeatures(
            List<String> featureKeys,
            Class<T> valueTypeClass,
            UserContext userContext
    ) {
        return core.evalFeatures(featureKeys, valueTypeClass, getEvalContext(userContext));
    }

    /**
     * Checks whether a feature evaluates to on for a user.
     *
     * @param featureKey feature key
     * @param userContext user context
     * @return true when the feature is on
     */
    public Boolean isOn(String featureKey, UserContext userContext) {
        return core.evalFeature(featureKey, Object.class, getEvalContext(userContext)).isOn();
    }

    /**
     * Checks whether a feature evaluates to off for a user.
     *
     * @param featureKey feature key
     * @param userContext user context
     * @return true when the feature is off
     */
    public Boolean isOff(String featureKey, UserContext userContext) {
        return core.evalFeature(featureKey, Object.class, getEvalContext(userContext)).isOff();
    }

    /**
     * Evaluates a feature and returns its value, falling back to the supplied default on missing or invalid values.
     *
     * @param featureKey feature key
     * @param defaultValue fallback value
     * @param gsonDeserializableClass expected value class
     * @param userContext user context
     * @param <T> feature value type
     * @return evaluated feature value or default value
     */
    public <T> T getFeatureValue(String featureKey, T defaultValue,
                                 Class<T> gsonDeserializableClass,
                                 UserContext userContext) {
        Object evaluatedValue = core.evalFeature(featureKey, gsonDeserializableClass, getEvalContext(userContext)).getValue();
        return FeatureValues.convert(evaluatedValue, defaultValue, gsonDeserializableClass);
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

    /**
     * Runs an experiment for a user and notifies experiment subscribers when assignment changes.
     *
     * @param experiment experiment to evaluate
     * @param userContext user context
     * @param <T> experiment value type
     * @return experiment evaluation result
     */
    public <T> ExperimentResult<T> run(Experiment<T> experiment, UserContext userContext) {
        return core.run(experiment, getEvalContext(userContext, inlineIdentifierAttributes(experiment)));
    }

    /**
     * Async twin of {@link #evalFeature(String, Class, UserContext)}: evaluates without blocking the
     * caller on sticky bucket or remote-eval I/O. With no sticky bucket service configured and local
     * evaluation, the returned future is already complete.
     *
     * <p>These rules apply to every {@code ...Async} evaluation method:
     * <ul>
     *   <li>Evaluation, tracking callbacks and the caller's continuations run on the client's
     *       executor or on the calling thread, not on the sticky bucket store's completion thread.
     *       The one exception: when the executor rejects the continuation, no other thread is left,
     *       so the future fails on the thread that completed the read.</li>
     *   <li>A sticky bucket read that fails or times out does not fail the future: the evaluation
     *       continues with normal bucketing and saves nothing.</li>
     *   <li>The future completes exceptionally when the executor rejects the work, when building
     *       the evaluation fails, or when {@link #shutdown()} runs before the evaluation does: an
     *       evaluation still waiting on its store read is failed, not bucketed without it. Nothing is
     *       thrown on the calling thread: a null {@code FeatureKey} fails the future too.</li>
     *   <li>The user context and its attributes must not be modified until the future completes.</li>
     *   <li>Only an {@link AsyncStickyBucketService} keeps the wait free of threads. A blocking
     *       {@code StickyBucketService} holds one executor thread per store call, and remote
     *       evaluation holds one per evaluation. The continuation runs on the same executor, so while
     *       every one of its threads is waiting on a blocking store,
     *       {@link Options#getStickyBucketTimeout()} cannot release an evaluation until one is free.</li>
     * </ul>
     *
     * @param key            feature key
     * @param valueTypeClass expected value class
     * @param userContext    user context
     * @param <ValueType>    feature value type
     * @return a future completing with the feature result
     */
    public <ValueType> CompletableFuture<FeatureResult<ValueType>> evalFeatureAsync(String key,
                                                                                    Class<ValueType> valueTypeClass,
                                                                                    UserContext userContext) {
        return trackAsyncEvaluation(getEvalContextAsync(userContext)
                .thenApply(evalContext -> core.evalFeature(key, valueTypeClass, evalContext)));
    }

    /**
     * Async twin of {@link #isOn(String, UserContext)}.
     *
     * @param featureKey  feature key
     * @param userContext user context
     * @return a future completing with whether the feature is on
     */
    public CompletableFuture<Boolean> isOnAsync(String featureKey, UserContext userContext) {
        return trackAsyncEvaluation(getEvalContextAsync(userContext)
                .thenApply(evalContext -> core.evalFeature(featureKey, Object.class, evalContext).isOn()));
    }

    /**
     * Async twin of {@link #isOff(String, UserContext)}.
     *
     * @param featureKey  feature key
     * @param userContext user context
     * @return a future completing with whether the feature is off
     */
    public CompletableFuture<Boolean> isOffAsync(String featureKey, UserContext userContext) {
        return trackAsyncEvaluation(getEvalContextAsync(userContext)
                .thenApply(evalContext -> core.evalFeature(featureKey, Object.class, evalContext).isOff()));
    }

    /**
     * Async twin of {@link #getFeatureValue(String, Object, Class, UserContext)}: same deserialization
     * and default-value semantics. Unlike the other async methods, the returned future never completes
     * exceptionally: a rejecting executor or a {@link #shutdown()} that comes first gives the default.
     *
     * @param featureKey              feature key
     * @param defaultValue            value when the feature is missing or evaluation fails
     * @param gsonDeserializableClass expected value class
     * @param userContext             user context
     * @param <ValueType>             feature value type
     * @return a future completing with the deserialized value or the default
     */
    public <ValueType> CompletableFuture<ValueType> getFeatureValueAsync(String featureKey,
                                                                         ValueType defaultValue,
                                                                         Class<ValueType> gsonDeserializableClass,
                                                                         UserContext userContext) {
        // One stage, so cancelling the returned future skips the evaluation like the other async methods.
        CompletableFuture<ValueType> future = getEvalContextAsync(userContext).handle((evalContext, error) -> {
            if (error != null) {
                log.warn("Async feature value evaluation failed for {}; returning default", featureKey, error);
                return defaultValue;
            }
            try {
                Object evaluatedValue = core.evalFeature(featureKey, gsonDeserializableClass, evalContext).getValue();
                return FeatureValues.convert(evaluatedValue, defaultValue, gsonDeserializableClass);
            } catch (RuntimeException e) {
                log.error("Async feature value evaluation failed for {}; returning default", featureKey, e);
                return defaultValue;
            }
        });
        return completeOnShutdown(future, () -> future.complete(defaultValue));
    }

    /**
     * Async twin of {@link #getFeature(FeatureKey, UserContext)}.
     *
     * @param featureKey  typed feature key
     * @param userContext user context
     * @param <T>         feature value type carried by the key
     * @return a future completing with the feature result; it fails with a
     *         {@code NullPointerException} when {@code featureKey} is null
     */
    public <T> CompletableFuture<FeatureResult<T>> getFeatureAsync(FeatureKey<T> featureKey, UserContext userContext) {
        if (featureKey == null) {
            return failedFuture(new NullPointerException("featureKey"));
        }
        return evalFeatureAsync(featureKey.getKey(), featureKey.getValueType(), userContext);
    }

    /**
     * Async twin of {@link #getFeatureValue(FeatureKey, Object, UserContext)}.
     *
     * @param featureKey   typed feature key
     * @param defaultValue value when the feature is missing or evaluation fails
     * @param userContext  user context
     * @param <T>          feature value type carried by the key
     * @return a future completing with the deserialized value or the default; it fails with a
     *         {@code NullPointerException} when {@code featureKey} is null
     */
    public <T> CompletableFuture<T> getFeatureValueAsync(FeatureKey<T> featureKey,
                                                         T defaultValue,
                                                         UserContext userContext) {
        if (featureKey == null) {
            return failedFuture(new NullPointerException("featureKey"));
        }
        return getFeatureValueAsync(featureKey.getKey(), defaultValue, featureKey.getValueType(), userContext);
    }

    /**
     * Async twin of {@link #run(Experiment, UserContext)}: evaluates the inline experiment and
     * notifies experiment subscribers when assignment changes, without blocking on sticky bucket I/O.
     *
     * @param experiment  inline experiment
     * @param userContext user context
     * @param <ValueType> variation value type
     * @return a future completing with the experiment result
     */
    public <ValueType> CompletableFuture<ExperimentResult<ValueType>> runAsync(Experiment<ValueType> experiment,
                                                                               UserContext userContext) {
        return trackAsyncEvaluation(getEvalContextAsync(userContext, inlineIdentifierAttributes(experiment))
                .thenApply(evalContext -> core.run(experiment, evalContext)));
    }

    /**
     * Shrinks the current features payload for one user, e.g. to bootstrap a client SDK during
     * server-side rendering. The result evaluates the same as the full payload for anyone who
     * agrees with the known attributes: this client's global attributes overlaid by the user's.
     *
     * <p>The payload is the one held by this client's feature repository: its features and saved
     * groups. The repository doesn't keep auto experiments or contextual bandits, so the result's
     * {@code experiments} list is empty. To reduce a whole payload, use
     * {@link PayloadReducer#reducePayload}. Safe for concurrent use.
     *
     * @param userContext the attributes known for the user; {@code null} for none
     * @param options     filters and prerequisite handling; {@code null} for defaults
     * @return a new payload JSON object with {@code features}, {@code experiments} and {@code savedGroups}
     */
    public JsonObject reducePayload(@Nullable UserContext userContext, @Nullable ReducePayloadOptions options) {
        JsonObject attributes = new JsonObject();
        JsonObject globalAttributes = this.options.getGlobalAttributes();
        if (globalAttributes != null) {
            for (Map.Entry<String, JsonElement> entry : globalAttributes.entrySet()) {
                attributes.add(entry.getKey(), entry.getValue());
            }
        }
        if (userContext != null && userContext.getAttributes() != null) {
            for (Map.Entry<String, JsonElement> entry : userContext.getAttributes().entrySet()) {
                attributes.add(entry.getKey(), entry.getValue());
            }
        }

        JsonObject payload = new JsonObject();
        GBFeaturesRepository repository = currentRepository();
        if (repository != null) {
            FeatureSnapshot snapshot = repository.getFeatureSnapshot();
            JsonElement features = GrowthBookJsonUtils.getInstance().gson.fromJson(snapshot.getFeaturesJson(), JsonElement.class);
            if (features != null && features.isJsonObject()) {
                payload.add("features", features);
            }
            payload.add("savedGroups", snapshot.getParsedSavedGroups());
        }
        return PayloadReducer.reducePayload(payload, attributes, options);
    }

    /**
     * Shrinks the current features payload for one user with the default options.
     *
     * @param userContext the attributes known for the user; {@code null} for none
     * @return a new payload JSON object
     * @see #reducePayload(UserContext, ReducePayloadOptions)
     */
    public JsonObject reducePayload(@Nullable UserContext userContext) {
        return reducePayload(userContext, null);
    }

    /**
     * Registers an experiment run callback.
     *
     * @param callback callback invoked when an experiment assignment changes
     */
    public void subscribe(ExperimentRunCallback callback) {
        core.subscribe(callback);
    }

    /**
     * Adds a listener that is notified after a feature refresh succeeds or fails.
     *
     * <p>Listeners never run on the thread that performed the refresh: they are dispatched on
     * {@link Options#getFeatureRefreshListenerExecutor()}, or on a dedicated daemon thread when
     * none is configured. A listener may therefore block without stalling polling or SSE updates.
     * If that executor rejects the task the event is dropped with a warning rather than run
     * inline, so configuring a rejecting executor gives back-pressure, not blocking.
     *
     * @param listener listener to add
     */
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        this.featureRefreshListeners.add(listener);
    }

    /**
     * Adds a listener and returns an idempotent handle that removes it.
     *
     * <p>Dispatch and threading are as described on {@link #addFeatureRefreshListener}.
     *
     * @param listener listener to add
     * @return subscription handle
     */
    public FeatureRefreshSubscription subscribeFeatureRefreshListener(FeatureRefreshListener listener) {
        return this.featureRefreshListeners.subscribe(listener);
    }

    /**
     * Removes a previously registered feature refresh listener.
     *
     * @param listener listener to remove
     */
    public void removeFeatureRefreshListener(FeatureRefreshListener listener) {
        this.featureRefreshListeners.remove(listener);
    }

    /**
     * Stops background work and releases resources: plugins are closed first, so they can flush
     * buffered events, then sticky bucketing and the async executor, then every async operation still
     * pending is resolved, then the remote-eval coordinator, the feature repository and the listener
     * executor. A step that fails is logged and the others still run.
     *
     * <p>Synchronized so concurrent shutdown calls cannot interleave the teardown.
     */
    public synchronized void shutdown() {
        this.clientShutdown.set(true);
        core.close(this::closeStickyBucketing,
                this::resolvePendingAsync,
                remoteEvalCoordinator::shutdown,
                featureRepositoryProvider::shutdown,
                listenerExecutor::shutdown);
    }

    private void closeStickyBucketing() {
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
    }

    /**
     * Resolves every async operation that is still pending, so a caller is never left waiting on a task
     * the executor's {@code shutdownNow()} discarded. A continuation that runs later finds its future
     * complete and skips the evaluation.
     */
    private void resolvePendingAsync() {
        for (Runnable onShutdown : new ArrayList<>(pendingAsync.values())) {
            onShutdown.run();
        }
    }

    private void handleInternalRefresh(GBFeaturesRepository repositorySnapshot, FeatureRefreshEvent event) {
        if (event.isFeaturesChanged()) {
            refreshGlobalContext(repositorySnapshot);
        }

        featureRefreshListeners.publish(event);
    }

    private void refreshGlobalContext(GBFeaturesRepository repositorySnapshot) {
        if (repositorySnapshot == null) {
            log.debug("Skipping global context refresh because repository is not available.");
            return;
        }
        try {
            globalContextManager.refresh(repositorySnapshot);
        } catch (RuntimeException e) {
            log.warn("Unable to refresh global context with latest features", e);
        }
    }

    private EvaluationContext getEvalContext(UserContext userContext) {
        return getEvalContext(userContext, Collections.<String>emptySet());
    }

    private EvaluationContext getEvalContext(UserContext userContext, Set<String> inlineIdentifierAttributes) {
        UserContext safeUserContext = userContext == null ? UserContext.builder().build() : userContext;
        // Capture ONE feature snapshot for both identifier derivation and evaluation, so a refresh
        // landing mid-read cannot pair new rules with documents fetched for the old identifiers.
        GlobalContext snapshot = this.globalContextManager.getCurrentGlobalContext();
        boolean stickyReadFailed = false;
        if (this.stickyBucketManager != null && safeUserContext.getStickyBucketAssignmentDocs() == null) {
            // Scoped read: fetch the user's sticky docs through the manager (coalesced, cached,
            // overlaid with this process's own writes) and populate the context so the managers' own
            // synchronous preload skips. A blocking service is read on this thread.
            JsonObject mergedAttributes = mergeGlobalAttributes(safeUserContext.getAttributes());
            Map<String, String> identifiers =
                    stickyIdentifierAttributesFor(mergedAttributes, snapshot, inlineIdentifierAttributes);
            Map<String, StickyAssignmentsDocument> docs = fetchStickyDocsBlocking(identifiers);
            stickyReadFailed = docs == null;
            UserContext scoped = safeUserContext.withAttributes(safeUserContext.getAttributes());
            scoped.setStickyBucketAssignmentDocs(
                    stickyReadFailed ? this.stickyBucketManager.localAssignments(identifiers) : docs);
            safeUserContext = scoped;
        }
        return buildEvalContext(safeUserContext, snapshot, stickyWriter(stickyReadFailed, true));
    }

    private CompletableFuture<EvaluationContext> getEvalContextAsync(UserContext userContext) {
        return getEvalContextAsync(userContext, Collections.<String>emptySet());
    }

    /**
     * The evaluation context for an async evaluation. Every failure is reported through the returned
     * future, including one that happens before there is anything to wait for.
     */
    private CompletableFuture<EvaluationContext> getEvalContextAsync(UserContext userContext,
                                                                     Set<String> inlineIdentifierAttributes) {
        try {
            return startEvalContextAsync(userContext, inlineIdentifierAttributes);
        } catch (RuntimeException e) {
            return failedFuture(e);
        }
    }

    private CompletableFuture<EvaluationContext> startEvalContextAsync(UserContext userContext,
                                                                       Set<String> inlineIdentifierAttributes) {
        if (clientShutdown.get()) {
            throw shutDownError();
        }
        UserContext safeUserContext = userContext == null ? UserContext.builder().build() : userContext;
        GlobalContext snapshot = this.globalContextManager.getCurrentGlobalContext();
        if (this.options.isRemoteEvalEnabled()) {
            // No sticky read here: options validation rejects remote evaluation combined with a
            // sticky bucket service, so building the context fails before anything is evaluated.
            return supplyOnExecutor(() -> buildEvalContext(safeUserContext, snapshot, stickyWriter(false, false)));
        }
        if (this.stickyBucketManager == null || safeUserContext.getStickyBucketAssignmentDocs() != null) {
            return CompletableFuture.completedFuture(
                    buildEvalContext(safeUserContext, snapshot, stickyWriter(false, false)));
        }
        Map<String, String> identifiers = stickyIdentifierAttributesFor(
                mergeGlobalAttributes(safeUserContext.getAttributes()), snapshot, inlineIdentifierAttributes);
        UserContext scoped = safeUserContext.withAttributes(safeUserContext.getAttributes());
        // handleAsync (not thenApplyAsync) so BOTH success and failure hop to the executor: a failing
        // store must not run evaluation or caller continuations on its own completion thread, for
        // example a Netty event loop during an outage.
        return this.stickyBucketManager.fetchAssignments(identifiers)
                .handleAsync((docs, error) -> {
                    if (clientShutdown.get()) {
                        // shutdown() cut this evaluation's wait short. Fail it: bucketing now would
                        // serve a variation chosen without the user's stored assignment.
                        throw shutDownError();
                    }
                    boolean stickyReadFailed = error != null;
                    if (stickyReadFailed) {
                        logFailedStickyRead(error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error);
                    }
                    scoped.setStickyBucketAssignmentDocs(
                            stickyReadFailed ? this.stickyBucketManager.localAssignments(identifiers) : docs);
                    return buildEvalContext(scoped, snapshot, stickyWriter(stickyReadFailed, false));
                }, this.asyncExecutor);
    }

    private EvaluationContext buildEvalContext(UserContext safeUserContext, GlobalContext snapshot,
                                               @Nullable StickyBucketDocWriter stickyWriter) {
        EvaluationContext context = this.options.isRemoteEvalEnabled()
                ? this.remoteEvalCoordinator.createEvaluationContext(safeUserContext)
                : this.globalContextManager.createEvaluationContext(safeUserContext, snapshot);
        if (stickyWriter != null) {
            context.setStickyBucketDocWriter(stickyWriter);
        }
        return withInstanceScope(context);
    }

    /**
     * The sticky bucket document writer for one evaluation.
     *
     * @param stickyReadFailed whether the user's stored documents could not be read; the evaluation
     *                         then buckets normally and its assignments are dropped, because saving
     *                         them could replace the variation the store already holds
     * @param callerMayBlock   true for the synchronous API, which writes a blocking service on the
     *                         calling thread; false for the asynchronous API, which never blocks
     * @return the writer, or {@code null} when sticky bucketing is not configured
     */
    @Nullable
    private StickyBucketDocWriter stickyWriter(boolean stickyReadFailed, boolean callerMayBlock) {
        if (this.stickyBucketManager == null) {
            return null;
        }
        if (stickyReadFailed) {
            return StickyBucketDocWriter.DISCARDING;
        }
        return callerMayBlock
                ? this.stickyBucketManager::recordAndSaveOnCallerThread
                : this.stickyBucketManager::recordAndSave;
    }

    private static void logFailedStickyRead(Throwable cause) {
        log.warn("Sticky bucket service failed to load assignments; continuing with normal bucketing"
                + " and not saving this evaluation's assignments.", cause);
    }

    /** The sticky identifier attributes an inline experiment reads, so its persisted assignment is loaded. */
    private static Set<String> inlineIdentifierAttributes(@Nullable Experiment<?> experiment) {
        if (experiment == null) {
            return Collections.emptySet();
        }
        Set<String> identifiers = new HashSet<>();
        identifiers.add(experiment.getHashAttribute() != null ? experiment.getHashAttribute() : "id");
        if (experiment.getFallbackAttribute() != null) {
            identifiers.add(experiment.getFallbackAttribute());
        }
        return identifiers;
    }

    /**
     * Prefetch sticky bucket assignments for a user context, so later evaluations with the same
     * context perform no sticky bucket I/O. The context's {@code stickyBucketAssignmentDocs} is
     * populated and the same instance is returned. The populated map is concurrency-safe, so the
     * returned context may be reused across concurrent evaluations.
     *
     * <p>The read covers the attributes the loaded features' experiments hash on, plus
     * {@code Options.stickyBucketIdentifierAttributes}. An inline experiment later passed to
     * {@code run} with this context is covered only if its hash attribute is one of those; list it
     * in {@code stickyBucketIdentifierAttributes} otherwise, or its stored assignment is not read.
     *
     * @param userContext the request-scoped user context to prefetch for
     * @return a future completing with the same context, docs populated
     */
    public CompletableFuture<UserContext> prefetchStickyBuckets(UserContext userContext) {
        UserContext context = userContext == null ? UserContext.builder().build() : userContext;
        if (this.stickyBucketManager == null || context.getStickyBucketAssignmentDocs() != null) {
            return CompletableFuture.completedFuture(context);
        }
        GlobalContext snapshot = this.globalContextManager.getCurrentGlobalContext();
        Map<String, String> identifiers = stickyIdentifierAttributesFor(
                mergeGlobalAttributes(context.getAttributes()), snapshot, Collections.<String>emptySet());
        return this.stickyBucketManager.fetchAssignments(identifiers)
                .thenApply(docs -> {
                    context.setStickyBucketAssignmentDocs(docs);
                    return context;
                });
    }

    /**
     * Completes when every pending sticky bucket save has been <em>attempted</em>. This is not a
     * durability guarantee: a save the store rejects is logged and kept for the next write or flush,
     * so a completed flush future means "no save is still outstanding", not "every assignment
     * reached the store".
     * Fire-and-forget writes do not need this; short-lived processes should await it (or call
     * {@link #shutdown()}, which flushes automatically) before exiting to drain in-flight saves.
     *
     * @return a future completing when all pending saves have been attempted
     */
    public CompletableFuture<Void> flushStickyBucketSaves() {
        if (this.stickyBucketManager == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.stickyBucketManager.flush();
    }

    private JsonObject mergeGlobalAttributes(@Nullable JsonObject userAttributes) {
        JsonObject merged = this.options.getGlobalAttributes() == null
                ? new JsonObject() : this.options.getGlobalAttributes().deepCopy();
        if (userAttributes != null) {
            for (Map.Entry<String, JsonElement> entry : userAttributes.entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private Map<String, String> stickyIdentifierAttributesFor(JsonObject mergedAttributes,
                                                              GlobalContext snapshot,
                                                              Set<String> extraIdentifiers) {
        Set<String> identifiers = new HashSet<>(resolveStickyIdentifierAttributes(snapshot));
        if (extraIdentifiers != null) {
            identifiers.addAll(extraIdentifiers);
        }
        return StickyAttributeValues.of(mergedAttributes, identifiers);
    }

    /**
     * Every attribute a loaded feature's experiment can hash on, plus the configured ones. The
     * configured list only adds to the derived set: an experiment whose hash attribute were left
     * unread would be bucketed again and its stored assignment replaced.
     */
    private Set<String> resolveStickyIdentifierAttributes(GlobalContext snapshot) {
        Set<String> derived = snapshot.getDerivedStickyIdentifierAttributes();
        if (derived == null) {
            derived = deriveStickyIdentifierAttributes(snapshot.getFeatures());
            snapshot.setDerivedStickyIdentifierAttributes(derived);
        }
        List<String> configured = this.options.getStickyBucketIdentifierAttributes();
        if (configured == null || configured.isEmpty()) {
            return derived;
        }
        Set<String> identifiers = new HashSet<>(derived);
        identifiers.addAll(configured);
        return identifiers;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<String> deriveStickyIdentifierAttributes(@Nullable Map<String, Feature<?>> features) {
        Set<String> attributes = new HashSet<>();
        if (features == null) {
            return attributes;
        }
        for (Feature<?> feature : features.values()) {
            if (feature == null || feature.getRules() == null) {
                continue;
            }
            for (FeatureRule rule : (List<FeatureRule>) (List) feature.getRules()) {
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
     * Reads the user's sticky bucket documents, blocking the calling thread until they arrive.
     *
     * @return the documents, or {@code null} when the store could not be read. The caller then
     * evaluates with this process's own assignments only and must not persist the result: a
     * variation bucketed without the stored assignment could replace it in the store.
     */
    @Nullable
    private Map<String, StickyAssignmentsDocument> fetchStickyDocsBlocking(Map<String, String> attributes) {
        try {
            return this.stickyBucketManager.fetchAssignmentsOnCallerThread(attributes).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching sticky bucket assignments", e);
        } catch (ExecutionException e) {
            logFailedStickyRead(e.getCause() == null ? e : e.getCause());
            return null;
        }
    }

    private EvaluationContext withInstanceScope(EvaluationContext context) {
        EvaluationContext scoped = core.scope(context);
        scoped.setSuppressTrackingErrors(true);
        return scoped;
    }

    @SuppressWarnings("deprecation")
    private void registerRefreshHandlers(GBFeaturesRepository repository) {
        repository.onFeaturesRefresh(this.options.getFeatureRefreshCallback());
        repository.addFeatureRefreshListener(event -> handleInternalRefresh(repository, event));
    }

    private GBFeaturesRepository currentRepository() {
        return featureRepositoryProvider.currentRepository();
    }
}
