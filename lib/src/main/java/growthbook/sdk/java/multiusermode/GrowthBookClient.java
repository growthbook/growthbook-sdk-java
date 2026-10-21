package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.diagnostics.model.Diagnostics;
import growthbook.sdk.java.diagnostics.provider.DiagnosticsProvider;
import growthbook.sdk.java.diagnostics.provider.GrowthBookClientDiagnosticsProvider;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
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
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.OptionsValidator;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.internal.ExperimentSubscriptionManager;
import growthbook.sdk.java.multiusermode.internal.FeatureRefreshListenerRegistry;
import growthbook.sdk.java.multiusermode.internal.FeatureRepositoryProvider;
import growthbook.sdk.java.multiusermode.internal.GlobalContextManager;
import growthbook.sdk.java.multiusermode.internal.ManagedListenerExecutor;
import growthbook.sdk.java.multiusermode.internal.RemoteEvalCoordinator;
import growthbook.sdk.java.plugin.PluginRegistry;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.SyncOffloadStickyBucketAdapter;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-user GrowthBook SDK facade.
 *
 * <p>The client owns one feature repository per instance in local mode. In remote-eval mode,
 * feature evaluation is fetched through the remote eval service and cached per user context.
 */
@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluator;
    private final GlobalContextManager globalContextManager;
    private final ManagedListenerExecutor listenerExecutor;
    private final RemoteEvalCoordinator remoteEvalCoordinator;
    private final FeatureRepositoryProvider featureRepositoryProvider;
    private final ExperimentSubscriptionManager experimentSubscriptions;
    private final FeatureRefreshListenerRegistry featureRefreshListeners;
    private final DiagnosticsProvider diagnosticsProvider;
    private final PluginRegistry pluginRegistry;

    private final AtomicReference<Throwable> lastInitializationError = new AtomicReference<>();
    private final AtomicLong lastInitializationErrorAtMillis = new AtomicLong(0);
    private final AtomicBoolean clientShutdown = new AtomicBoolean(false);

    /** Owns all sticky bucket I/O; null when no sticky bucket service is configured. */
    @Nullable
    private final StickyBucketManager stickyBucketManager;
    /** The pool this client created for itself (and must shut down); null when the caller supplied one. */
    @Nullable
    private final ExecutorService ownedAsyncExecutor;

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

        this.featureEvaluator = new FeatureEvaluator();
        this.experimentEvaluator = new ExperimentEvaluator();
        this.globalContextManager = new GlobalContextManager(this.options);
        this.experimentSubscriptions = new ExperimentSubscriptionManager();
        this.listenerExecutor = ManagedListenerExecutor.resolve(this.options.getFeatureRefreshListenerExecutor());
        this.featureRefreshListeners = new FeatureRefreshListenerRegistry(listenerExecutor.executor());
        this.remoteEvalCoordinator = new RemoteEvalCoordinator(this.options, this.featureRefreshListeners);
        this.featureRepositoryProvider = new FeatureRepositoryProvider(
                this.options,
                this::registerRefreshHandlers,
                this.globalContextManager::initialize
        );
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
     * Bounded daemon pool for blocking I/O offload — sized for I/O, not CPU. Fixed core==max with
     * idle timeout so a quiet client holds zero threads. Default AbortPolicy: a saturated pool
     * surfaces the rejection rather than running blocking store I/O on the request thread.
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
        } catch (Exception e) {
            recordInitializationFailure(e);
            log.error("Failed to initialize growthbook instance", e);
            return false;
        }
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
        return featureEvaluator.evaluateFeature(key, getEvalContext(userContext), valueTypeClass);
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
     * @param <ValueType>    the result value type
     * @return a map from feature key to its {@link FeatureResult}
     */
    public <ValueType> Map<String, FeatureResult<ValueType>> evalFeatures(
            List<String> featureKeys,
            Class<ValueType> valueTypeClass,
            UserContext userContext
    ) {
        return featureEvaluator.evaluateFeatures(featureKeys, getEvalContext(userContext), valueTypeClass);
    }

    /**
     * Checks whether a feature evaluates to on for a user.
     *
     * @param featureKey feature key
     * @param userContext user context
     * @return true when the feature is on
     */
    public Boolean isOn(String featureKey, UserContext userContext) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvalContext(userContext), Object.class).isOn();
    }

    /**
     * Checks whether a feature evaluates to off for a user.
     *
     * @param featureKey feature key
     * @param userContext user context
     * @return true when the feature is off
     */
    public Boolean isOff(String featureKey, UserContext userContext) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvalContext(userContext), Object.class).isOff();
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
        try {
            Object evaluatedValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvalContext(userContext), gsonDeserializableClass).getValue();

            if (evaluatedValue == null) {
                return defaultValue;
            }

            String stringValue = GrowthBookJsonUtils.getInstance().gson.toJson(evaluatedValue);

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

    /**
     * Runs an experiment for a user and notifies experiment subscribers when assignment changes.
     *
     * @param experiment experiment to evaluate
     * @param userContext user context
     * @param <T> experiment value type
     * @return experiment evaluation result
     */
    public <T> ExperimentResult<T> run(Experiment<T> experiment, UserContext userContext) {
        ExperimentResult<T> result = experimentEvaluator
                .evaluateExperiment(experiment, getEvalContext(userContext, inlineIdentifierAttributes(experiment)), null);

        experimentSubscriptions.publishIfChanged(experiment, result);

        return result;
    }

    /**
     * Registers an experiment run callback.
     *
     * @param callback callback invoked when an experiment assignment changes
     */
    public void subscribe(ExperimentRunCallback callback) {
        this.experimentSubscriptions.subscribe(callback);
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
     * Stops repository background work and releases repository resources.
     *
     * <p>Synchronized so concurrent shutdown calls cannot interleave teardown of the repository,
     * the remote-eval coordinator, the listener executor, and the plugin registry.
     */
    public synchronized void shutdown() {
        this.clientShutdown.set(true);
        // Drain sticky bucket saves while the offload executor is still alive — shutting it down
        // first would discard trailing saves and leave the flush waiting on futures nobody completes.
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
        featureRepositoryProvider.shutdown();
        this.remoteEvalCoordinator.shutdown();
        listenerExecutor.shutdown();
        // Flush registered plugins (including the built-in tracking plugin) so
        // any buffered events are sent before the client is discarded.
        this.pluginRegistry.closeAll();
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
        if (this.stickyBucketManager != null && safeUserContext.getStickyBucketAssignmentDocs() == null) {
            // Scoped async read: fetch the user's sticky docs through the manager (coalesced, cached,
            // overlaid with this process's own writes) and populate the context so the managers' own
            // synchronous preload skips. A read failure propagates out of eval (JS/Python parity).
            JsonObject mergedAttributes = mergeGlobalAttributes(safeUserContext.getAttributes());
            Map<String, String> identifiers =
                    stickyIdentifierAttributesFor(mergedAttributes, snapshot, inlineIdentifierAttributes);
            UserContext scoped = safeUserContext.withAttributes(safeUserContext.getAttributes());
            scoped.setStickyBucketAssignmentDocs(fetchStickyDocsBlocking(identifiers));
            safeUserContext = scoped;
        }
        EvaluationContext context = this.options.isRemoteEvalEnabled()
                ? this.remoteEvalCoordinator.createEvaluationContext(safeUserContext)
                : this.globalContextManager.createEvaluationContext(safeUserContext, snapshot);
        if (this.stickyBucketManager != null) {
            context.setStickyBucketDocWriter(this.stickyBucketManager::recordAndSave);
        }
        return withPluginRegistry(context);
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
     * durability guarantee: a save the store rejects is logged and not retried, so a completed flush
     * future means "no save is still outstanding", not "every assignment reached the store".
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
        Map<String, String> attributes = new HashMap<>(Math.max(4, identifiers.size() * 2));
        for (String name : identifiers) {
            JsonElement value = mergedAttributes.get(name);
            if (value != null && value.isJsonPrimitive()) {
                attributes.put(name, value.getAsString());
            }
        }
        return attributes;
    }

    private Set<String> resolveStickyIdentifierAttributes(GlobalContext snapshot) {
        List<String> configured = this.options.getStickyBucketIdentifierAttributes();
        if (configured != null && !configured.isEmpty()) {
            return new HashSet<>(configured);
        }
        Set<String> derived = snapshot.getDerivedStickyIdentifierAttributes();
        if (derived == null) {
            derived = deriveStickyIdentifierAttributes(snapshot.getFeatures());
            snapshot.setDerivedStickyIdentifierAttributes(derived);
        }
        return derived;
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
            throw new RuntimeException("Failed to fetch sticky bucket assignments", cause);
        }
    }

    /** Attaches this client's own plugin registry so events never route through another client's plugins. */
    private EvaluationContext withPluginRegistry(EvaluationContext context) {
        context.setPluginRegistry(this.pluginRegistry);
        return context;
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
