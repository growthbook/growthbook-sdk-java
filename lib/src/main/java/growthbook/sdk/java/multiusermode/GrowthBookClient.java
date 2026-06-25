package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.remoteeval.RemoteEvalCache;
import growthbook.sdk.java.remoteeval.RemoteEvalCacheKey;
import growthbook.sdk.java.remoteeval.RemoteEvalOptionsValidator;
import growthbook.sdk.java.remoteeval.RemoteEvalRequestBuilder;
import growthbook.sdk.java.remoteeval.RemoteEvalResponse;
import growthbook.sdk.java.remoteeval.RemoteEvalService;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
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

@Slf4j
public class GrowthBookClient {

    private static final int DEFAULT_SWR_TTL_SECONDS = 60;

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private GBFeaturesRepository repository;
    private RemoteEvalService remoteEvalService;
    private RemoteEvalCache remoteEvalCache;
    private List<ExperimentRunCallback> callbacks;
    private final Map<String, AssignedExperiment> assigned;
    private GlobalContext globalContext;

    public GrowthBookClient() {
        this(Options.builder().build());
    }

    public GrowthBookClient(Options opts) {
        this.options = opts == null ? Options.builder().build() : opts;

        this.assigned = new HashMap<>();
        this.callbacks = new ArrayList<>();
        this.featureEvaluator = new FeatureEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
    }

    public boolean initialize() {
        try {
            if (this.options.isRemoteEvalEnabled()) {
                return ensureRemoteEvalReady();
            }

            return initializeRepository();
        } catch (Exception e) {
            log.error("Failed to initialize growthbook instance", e);
            return false;
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

    public void refreshFeature() {
        if (this.options.isRemoteEvalEnabled()) {
            clearRemoteEvalCache();
            return;
        }

        if (repository == null) {
            log.warn("Cannot refresh features before GrowthBookClient is initialized.");
            return;
        }

        try {
            repository.fetchFeatures();
        } catch (FeatureFetchException e) {
            log.error("Refreshing wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    public void refreshForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) {
        try {
            if (this.options.isRemoteEvalEnabled()) {
                RemoteEvalResponse response = getRemoteEvalService().fetch(requestBodyForRemoteEval);
                this.globalContext = buildGlobalContext(response.getFeatures(), response.getSavedGroups());
                clearRemoteEvalCache();
                return;
            }

            if (repository == null) {
                log.warn("Cannot refresh remote evaluation features before GrowthBookClient is initialized.");
                return;
            }
            repository.fetchForRemoteEval(requestBodyForRemoteEval);
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
        if (repository != null) {
            repository.shutdown();
            repository = null;
            log.info("Repository shut down");
        }
        if (this.remoteEvalCache != null) {
            this.remoteEvalCache.shutdown();
        }
        if (this.remoteEvalService != null) {
            this.remoteEvalService.close();
        }
    }

    private boolean initializeRepository() throws FeatureFetchException {
        if (this.repository != null) {
            return this.repository.getInitialized();
        }

        GbCacheManager cacheManager = this.options.getCacheManager() != null
                ? this.options.getCacheManager()
                : CacheManagerFactory.create(this.options.getCacheMode(), this.options.getCacheDirectory());

        this.repository = GBFeaturesRepository.builder()
                .apiHost(this.options.getApiHost())
                .clientKey(this.options.getClientKey())
                .decryptionKey(this.options.getDecryptionKey())
                .refreshStrategy(this.options.getRefreshStrategy())
                .swrTtlSeconds(this.options.getSwrTtlSeconds())
                .isCacheDisabled(this.options.getIsCacheDisabled() || this.options.getCacheMode() == CacheMode.NONE)
                .cacheManager(cacheManager)
                .requestBodyForRemoteEval(configurePayloadForRemoteEval(this.options))
                .build();

        this.repository.onFeaturesRefresh(this.options.getFeatureRefreshCallback());
        this.repository.onFeaturesRefresh(this.refreshGlobalContext());
        try {
            this.repository.initialize();
        } catch (FeatureFetchException e) {
            this.repository = null;
            throw e;
        }
        this.globalContext = buildGlobalContext(this.repository.getParsedFeatures(), this.repository.getParsedSavedGroups());
        log.info("GrowthBookClient initialized repository and registered feature refresh callbacks.");

        return this.repository.getInitialized();
    }

    private boolean ensureRemoteEvalReady() {
        RemoteEvalOptionsValidator.validate(this.options);
        getRemoteEvalService();
        getRemoteEvalCache();
        initializeRemoteEvalSseInvalidationIfNeeded();
        if (this.globalContext == null) {
            this.globalContext = buildGlobalContext(Collections.emptyMap(), new JsonObject());
        }
        return true;
    }

    private void initializeRemoteEvalSseInvalidationIfNeeded() {
        if (this.options.getRefreshStrategy() != FeatureRefreshStrategy.SERVER_SENT_EVENTS || this.repository != null) {
            return;
        }

        this.repository = GBFeaturesRepository.builder()
                .apiHost(this.options.getApiHost())
                .clientKey(this.options.getClientKey())
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .isCacheDisabled(true)
                .build();
        this.repository.onFeaturesRefresh(new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                clearRemoteEvalCache();
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Unable to receive remote evaluation invalidation event", throwable);
            }
        });

        try {
            this.repository.initialize();
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
                GBFeaturesRepository currentRepository = repository;
                if (currentRepository == null) {
                    log.debug("Skipping global context refresh because repository is not initialized.");
                    return;
                }
                if (globalContext != null) {
                    globalContext.setFeatures(currentRepository.getParsedFeatures());
                    globalContext.setSavedGroups(currentRepository.getParsedSavedGroups());
                } else {
                    globalContext = GlobalContext.builder()
                            .features(currentRepository.getParsedFeatures())
                            .savedGroups(currentRepository.getParsedSavedGroups())
                            .enabled(options.getEnabled())
                            .qaMode(options.getIsQaMode())
                            .forcedFeatureValues(options.getGlobalForcedFeatureValues())
                            .forcedVariations(options.getGlobalForcedVariationsMap())
                            .build();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Unable to refresh global context with latest features", throwable);
            }
        };
    }

    private EvaluationContext getEvalContext(UserContext userContext) {
        UserContext updatedUserContext = toUserContextWithMergedAttributes(userContext);
        if (this.options.isRemoteEvalEnabled()) {
            return getRemoteEvalContext(updatedUserContext);
        }
        return new EvaluationContext(getLocalGlobalContext(), updatedUserContext, new EvaluationContext.StackContext(), this.options);
    }

    private EvaluationContext getRemoteEvalContext(UserContext userContext) {
        try {
            RemoteEvalResponse response = getRemoteEvalResponse(userContext);
            GlobalContext remoteGlobalContext = buildGlobalContext(response.getFeatures(), response.getSavedGroups());
            return new EvaluationContext(remoteGlobalContext, userContext, new EvaluationContext.StackContext(), this.options);
        } catch (FeatureFetchException e) {
            log.warn("Remote evaluation request failed. Falling back to local feature context.", e);
            return new EvaluationContext(getLocalGlobalContext(), userContext, new EvaluationContext.StackContext(), this.options);
        }
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
        return currentUserContext.withAttributes(merged);
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
                    secondsToDuration(this.options.getSwrTtlSeconds() == null ? DEFAULT_SWR_TTL_SECONDS : this.options.getSwrTtlSeconds()),
                    secondsToDuration(this.options.getRemoteEvalCacheTtlSeconds())
            );
        }
        return this.remoteEvalCache;
    }

    private static Duration secondsToDuration(@Nullable Integer seconds) {
        return seconds == null ? null : Duration.ofSeconds(seconds);
    }

    private void clearRemoteEvalCache() {
        if (this.remoteEvalCache != null) {
            this.remoteEvalCache.invalidateAll();
        }
    }

    private GlobalContext getLocalGlobalContext() {
        if (this.globalContext == null) {
            this.globalContext = buildGlobalContext(Collections.emptyMap(), new JsonObject());
        }
        return this.globalContext;
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
