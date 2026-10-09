package growthbook.sdk.java.multiusermode;

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
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.OptionsValidator;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.internal.ExperimentSubscriptionManager;
import growthbook.sdk.java.multiusermode.internal.FeatureRefreshListenerRegistry;
import growthbook.sdk.java.multiusermode.internal.FeatureRepositoryProvider;
import growthbook.sdk.java.multiusermode.internal.GlobalContextManager;
import growthbook.sdk.java.multiusermode.internal.ManagedListenerExecutor;
import growthbook.sdk.java.multiusermode.internal.RemoteEvalCoordinator;
import growthbook.sdk.java.multiusermode.usage.EventLoggerDispatch;
import growthbook.sdk.java.plugin.PluginRegistry;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.UserContextUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
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

        this.pluginRegistry = new PluginRegistry(this.options.getPlugins());
        this.pluginRegistry.initAll();
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
        return evalFeature(key, valueTypeClass, userContext, null);
    }

    /**
     * Returns a per-request scoped view bound to one user, whose evaluations buffer experiment
     * exposures for a single {@link UserScopedGrowthBook#fireDeferredTrackingCalls()} flush at the
     * end of the request. Direct {@code GrowthBookClient} evaluations remain immediate.
     *
     * @param userContext the user to bind; {@code null} yields an empty context
     * @return a new, non-thread-safe scoped view
     */
    public UserScopedGrowthBook forUser(UserContext userContext) {
        return new UserScopedGrowthBook(
                this, userContext == null ? UserContext.builder().build() : userContext);
    }

    /**
     * Emits an application-defined event to the configured
     * {@link growthbook.sdk.java.multiusermode.usage.EventLogger}. Fires immediately and is a no-op
     * when no event logger is configured. A throwing logger is logged and swallowed.
     *
     * @param eventName   the event name
     * @param properties  event properties; {@code null} is treated as empty
     * @param userContext the user the event is for
     */
    public void logEvent(String eventName, Map<String, Object> properties, UserContext userContext) {
        // No-op without a logger: skip building an evaluation context, which in remote mode would fetch
        // feature data on a cache miss and query any configured sticky-bucket service.
        if (this.options.getEventLogger() == null) {
            return;
        }
        UserContext mergedUser = UserContextUtils.withMergedAttributes(this.options, userContext);
        EventLoggerDispatch.logEvent(this.options, eventName, properties, mergedUser);
    }

    <T> FeatureResult<T> evalFeature(String key,
                                     Class<T> valueTypeClass,
                                     UserContext userContext,
                                     @Nullable DeferredTrackingBuffer buffer) {
        return featureEvaluator.evaluateFeature(key, getEvalContext(userContext, buffer), valueTypeClass);
    }

    void flushDeferredTracking(DeferredTrackingBuffer buffer) {
        buffer.flush(this.options, this.pluginRegistry);
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
        return isOn(featureKey, userContext, null);
    }

    Boolean isOn(String featureKey, UserContext userContext, @Nullable DeferredTrackingBuffer buffer) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvalContext(userContext, buffer), Object.class).isOn();
    }

    /**
     * Checks whether a feature evaluates to off for a user.
     *
     * @param featureKey feature key
     * @param userContext user context
     * @return true when the feature is off
     */
    public Boolean isOff(String featureKey, UserContext userContext) {
        return isOff(featureKey, userContext, null);
    }

    Boolean isOff(String featureKey, UserContext userContext, @Nullable DeferredTrackingBuffer buffer) {
        return this.featureEvaluator.evaluateFeature(featureKey, getEvalContext(userContext, buffer), Object.class).isOff();
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
        return getFeatureValue(featureKey, defaultValue, gsonDeserializableClass, userContext, null);
    }

    <T> T getFeatureValue(String featureKey, T defaultValue,
                          Class<T> gsonDeserializableClass,
                          UserContext userContext,
                          @Nullable DeferredTrackingBuffer buffer) {
        try {
            Object evaluatedValue = this.featureEvaluator
                    .evaluateFeature(featureKey, getEvalContext(userContext, buffer), gsonDeserializableClass).getValue();

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
        return run(experiment, userContext, null);
    }

    <T> ExperimentResult<T> run(Experiment<T> experiment, UserContext userContext, @Nullable DeferredTrackingBuffer buffer) {
        ExperimentResult<T> result = experimentEvaluator
                .evaluateExperiment(experiment, getEvalContext(userContext, buffer), null);

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
        UserContext safeUserContext = userContext == null ? UserContext.builder().build() : userContext;
        if (this.options.isRemoteEvalEnabled()) {
            return withPluginRegistry(this.remoteEvalCoordinator.createEvaluationContext(safeUserContext));
        }
        return withPluginRegistry(this.globalContextManager.createEvaluationContext(safeUserContext));
    }

    private EvaluationContext getEvalContext(UserContext userContext, @Nullable DeferredTrackingBuffer buffer) {
        EvaluationContext evaluationContext = getEvalContext(userContext);
        evaluationContext.setDeferredTracking(buffer);
        return evaluationContext;
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
