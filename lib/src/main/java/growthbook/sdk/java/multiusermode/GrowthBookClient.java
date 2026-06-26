package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.GrowthBookClientInitializationException;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.GrowthBookUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private List<ExperimentRunCallback> callbacks;
    private final FeatureEvaluator featureEvaluator;
    private final Map<String, AssignedExperiment> assigned;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private final AtomicReference<GlobalContext> globalContext = new AtomicReference<>();
    private final AtomicReference<GBFeaturesRepository> repository = new AtomicReference<>();

    public GrowthBookClient() {
        this(Options.builder().build());
    }

    public GrowthBookClient(Options opts) {
        this.options = opts == null ? Options.builder().build() : opts;

        this.assigned = new ConcurrentHashMap<>();
        this.callbacks = new CopyOnWriteArrayList<>();
        this.featureEvaluator = new FeatureEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
    }

    public boolean initialize() {
        GBFeaturesRepository repositoryToInitialize = null;
        try {
            repositoryToInitialize = prepareRepositoryForInitialization();
            if (repositoryToInitialize == null) {
                GBFeaturesRepository repositorySnapshot = this.repository.get();
                return repositorySnapshot != null && repositorySnapshot.getInitialized().get();
            }

            initializeFeaturesRepository(repositoryToInitialize);
            replaceGlobalContextFrom(repositoryToInitialize);

            boolean isReady = this.repository.get() == repositoryToInitialize
                    && repositoryToInitialize.getInitialized().get();
            if (isReady) {
                log.info("GrowthBookClient initialized repository and registered feature refresh callbacks.");
            }
            return isReady;
        } catch (RuntimeException e) {
            clearFailedInitialization(repositoryToInitialize);
            log.error("Failed to initialize growthbook instance", e);
            return false;
        }
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
    }

    public void setGlobalForceFeatures(Map<String, Object> forceFeatures) {
        this.options.setGlobalForcedFeatureValues(forceFeatures);
    }

    public void setGlobalForceVariations(Map<String, Integer> forceVariations) {
        this.options.setGlobalForcedVariationsMap(forceVariations);
    }

    @Deprecated
    public void refreshFeature() {
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

        GrowthBookUtils.fireSubscriptions(this.assigned, this.callbacks, experiment, result);

        return result;
    }

    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }

    public synchronized void shutdown() {
        GBFeaturesRepository repositorySnapshot = this.repository.getAndSet(null);
        this.globalContext.set(null);
        if (repositorySnapshot != null) {
            repositorySnapshot.shutdown();
            log.info("Repository shut down");
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
                .features(sourceRepository.getParsedFeatures().get())
                .savedGroups(sourceRepository.getParsedSavedGroups().get())
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }

    private EvaluationContext getEvalContext(UserContext userContext) {
        // Merge attributes using JsonObject to avoid parse/serialize churn
        JsonObject merged = new JsonObject();
        if (this.options.getGlobalAttributes() != null) {
            merged = GrowthBookJsonUtils.getInstance().gson.fromJson(this.options.getGlobalAttributes(), JsonObject.class);
            if (merged == null) merged = new JsonObject();
        }
        JsonObject userAttrs = userContext.getAttributes();
        if (userAttrs != null) {
            for (Map.Entry<String, JsonElement> e : userAttrs.entrySet()) {
                merged.add(e.getKey(), e.getValue());
            }
        }
        UserContext updatedUserContext = userContext.withAttributes(merged);

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

        return new EvaluationContext(this.globalContext.get(), updatedUserContext, new EvaluationContext.StackContext(), this.options);
    }

    private RequestBodyForRemoteEval configurePayloadForRemoteEval(Options options) {
        List<List<Object>> forceFeaturesForPayload = new ArrayList<>();
        if (options.getGlobalForcedFeatureValues() != null) {
            forceFeaturesForPayload = options.getGlobalForcedFeatureValues().entrySet().stream()
                    .map(entry -> Arrays.asList(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        }
        return new RequestBodyForRemoteEval(options.getGlobalAttributes(), forceFeaturesForPayload, options.getGlobalForcedVariationsMap(), options.getUrl());
    }
}
