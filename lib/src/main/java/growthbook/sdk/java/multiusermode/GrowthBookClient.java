package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.internal.ExperimentSubscriptionManager;
import growthbook.sdk.java.multiusermode.internal.FeatureRefreshListenerRegistry;
import growthbook.sdk.java.multiusermode.internal.FeatureRepositoryProvider;
import growthbook.sdk.java.multiusermode.internal.GlobalContextManager;
import growthbook.sdk.java.multiusermode.internal.ManagedListenerExecutor;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Multi-user GrowthBook SDK facade.
 *
 * <p>The client owns one feature repository per instance. Repository initialization is lazy,
 * idempotent, and shared by concurrent callers. Feature evaluation reads from the most recent
 * global context built from repository data.
 */
@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluator;
    private final GlobalContextManager globalContextManager;
    private final ExperimentSubscriptionManager experimentSubscriptions;
    private final FeatureRefreshListenerRegistry featureRefreshListeners;
    private final FeatureRepositoryProvider featureRepositoryProvider;
    private final ManagedListenerExecutor listenerExecutor;

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
        this.featureRepositoryProvider = new FeatureRepositoryProvider(
                this.options,
                this::registerRefreshHandlers,
                this.globalContextManager::initialize
        );
    }

    /**
     * Initializes the feature repository and global evaluation context.
     *
     * <p>Calling this method multiple times is safe. Concurrent calls share the same repository
     * initialization attempt. If initialization fails, a later call may try to initialize again.
     *
     * @return true when the repository is initialized and ready for evaluation
     */
    public boolean initialize() {
        GBFeaturesRepository repository = featureRepositoryProvider.initialize();
        return repository != null && repository.getInitialized();
    }

    /**
     * Replaces global attributes used for future evaluations.
     *
     * @param attributes JSON string containing global attributes
     */
    public void setGlobalAttributes(String attributes) {
        this.options.setGlobalAttributes(attributes);
    }

    /**
     * Replaces globally forced feature values used for future evaluations.
     *
     * @param forceFeatures feature key to forced value map
     */
    public void setGlobalForceFeatures(Map<String, Object> forceFeatures) {
        this.options.setGlobalForcedFeatureValues(forceFeatures);
    }

    /**
     * Replaces globally forced variations used for future experiment evaluations.
     *
     * @param forceVariations experiment key to forced variation index map
     */
    public void setGlobalForceVariations(Map<String, Integer> forceVariations) {
        this.options.setGlobalForcedVariationsMap(forceVariations);
    }

    /**
     * Fetches the latest feature definitions using the default refresh path.
     *
     * <p>The client must be initialized first. Calling this method before initialization logs a
     * warning and returns without throwing.
     */
    public void refreshFeature() {
        GBFeaturesRepository repositorySnapshot = currentRepository();
        if (repositorySnapshot == null) {
            log.warn("Cannot refresh features before GrowthBookClient is initialized.");
            return;
        }

        try {
            repositorySnapshot.fetchFeatures();
        } catch (FeatureFetchException e) {
            log.error("Refreshing wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    /**
     * Refreshes feature definitions using remote evaluation payload.
     *
     * <p>The client must be initialized first. Calling this method before initialization logs a
     * warning and returns without throwing.
     *
     * @param requestBodyForRemoteEval remote evaluation request payload
     */
    public void refreshForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) {
        GBFeaturesRepository repositorySnapshot = currentRepository();
        if (repositorySnapshot == null) {
            log.warn("Cannot refresh remote evaluation features before GrowthBookClient is initialized.");
            return;
        }

        try {
            repositorySnapshot.fetchForRemoteEval(requestBodyForRemoteEval);
        } catch (FeatureFetchException e) {
            log.error("Refreshing for remote eval wasn't successful. Message is: {}", e.getMessage(), e);
        }
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
     * Runs an experiment for a user and notifies experiment subscribers when assignment changes.
     *
     * @param experiment experiment to evaluate
     * @param userContext user context
     * @param <T> experiment value type
     * @return experiment evaluation result
     */
    public <T> ExperimentResult<T> run(Experiment<T> experiment, UserContext userContext) {
        ExperimentResult<T> result = experimentEvaluator
                .evaluateExperiment(experiment, getEvalContext(userContext), null);

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
     * <p>Listener failures are isolated from SDK refresh processing. Notifications are dispatched off
     * the refresh thread: through the executor configured in {@link Options} when supplied, otherwise
     * on a dedicated daemon thread owned by this client.
     *
     * <p>To observe the initial {@code INITIALIZATION} refresh, register the listener before
     * calling {@link #initialize()}; events emitted before registration are not replayed.
     *
     * @param listener listener to add
     */
    public void addFeatureRefreshListener(FeatureRefreshListener listener) {
        this.featureRefreshListeners.add(listener);
    }

    /**
     * Adds a listener and returns an idempotent handle that removes it.
     *
     * <p>To observe the initial {@code INITIALIZATION} refresh, register the listener before
     * calling {@link #initialize()}; events emitted before registration are not replayed.
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
     * <p>This method is idempotent. If initialization is still in progress, the client cancels its
     * ownership of that initialization and closes the repository once the initialization attempt
     * exits.
     */
    public void shutdown() {
        featureRepositoryProvider.shutdown();
        listenerExecutor.shutdown();
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
        return this.globalContextManager.createEvaluationContext(userContext);
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
