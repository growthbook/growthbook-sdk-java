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
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private List<ExperimentRunCallback> callbacks;
    private final FeatureEvaluator featureEvaluator;
    private final Map<String, AssignedExperiment> assigned;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private final AtomicReference<GlobalContext> globalContext = new AtomicReference<>();
    private final AtomicReference<GBFeaturesRepository> repository = new AtomicReference<>();
    private volatile RemoteEvalService remoteEvalService;
    private volatile RemoteEvalCache remoteEvalCache;
    private final AtomicBoolean remoteEvalReady = new AtomicBoolean(false);
    private final AtomicBoolean clientShutdown = new AtomicBoolean(false);
    private final AtomicReference<Throwable> lastInitializationError = new AtomicReference<>();
    private final AtomicLong lastInitializationErrorAtMillis = new AtomicLong(0);
    private final DiagnosticsProvider diagnosticsProvider;
    private final PluginRegistry pluginRegistry;

    public GrowthBookClient() {
        this(Options.builder().build());
    }

    public GrowthBookClient(Options opts) {
        this.options = opts == null ? Options.builder().build() : opts;

        this.assigned = new HashMap<>();
        this.callbacks = new ArrayList<>();
        this.featureEvaluator = new FeatureEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
        this.diagnosticsProvider = new GrowthBookClientDiagnosticsProvider(this.options, clientStateView());

        this.pluginRegistry = new PluginRegistry(this.options.getPlugins());
        this.pluginRegistry.initAll();
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

    public synchronized void shutdown() {
        this.clientShutdown.set(true);
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
        synchronized (this) {
            if (this.remoteEvalReady.get()) {
                return true;
            }
            RemoteEvalOptionsValidator.validate(this.options);
            getRemoteEvalService();
            getRemoteEvalCache();
            initializeRemoteEvalSseInvalidationIfNeeded();
            this.globalContext.compareAndSet(null, buildGlobalContext(Collections.emptyMap(), new JsonObject()));
            this.remoteEvalReady.set(true);
            return true;
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
        String key = experiment.getKey();
        // If assigned variation has changed, fire subscriptions
        AssignedExperiment prev = this.assigned.get(key);
        if (prev == null
                || !Objects.equals(prev.getInExperiment(), result.getInExperiment())
                || !Objects.equals(prev.getVariationId(), result.getVariationId())) {
            AssignedExperiment current = new AssignedExperiment(
                    experiment.getKey(),
                    result.getInExperiment(),
                    result.getVariationId()
            );
            this.assigned.put(key, current);

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
        return GlobalContext.builder()
                .features(sourceRepository.getParsedFeatures())
                .savedGroups(sourceRepository.getParsedSavedGroups())
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
        return withPluginRegistry(new EvaluationContext(getLocalGlobalContext(), updatedUserContext, new EvaluationContext.StackContext(), this.options));
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

    private UserContext toUserContextWithMergedAttributes(UserContext userContext) {
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
        UserContext updatedUserContext = currentUserContext.withAttributes(merged);

        // If a sticky bucket service is configured and the caller hasn't preloaded docs,
        // fetch docs for this user's attributes now (one call per request).
        if (this.options.getStickyBucketService() != null
                && updatedUserContext.getStickyBucketAssignmentDocs() == null) {
            Map<String, String> attrStrings = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : merged.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    attrStrings.put(e.getKey(), e.getValue().getAsString());
                }
            }
            Map<String, StickyAssignmentsDocument> docs =
                    this.options.getStickyBucketService().getAllAssignments(attrStrings);
            updatedUserContext.setStickyBucketAssignmentDocs(docs);
        }

        return updatedUserContext;
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

    private synchronized RemoteEvalService getRemoteEvalService() {
        if (this.remoteEvalService == null) {
            this.remoteEvalService = new RemoteEvalService(this.options.getApiHost(), this.options.getClientKey());
        }
        return this.remoteEvalService;
    }

    private synchronized RemoteEvalCache getRemoteEvalCache() {
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
