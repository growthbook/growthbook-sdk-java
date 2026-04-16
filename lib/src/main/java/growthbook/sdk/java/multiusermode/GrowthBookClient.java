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
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.GrowthBookUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private static volatile GBFeaturesRepository repository;
    private final List<ExperimentRunCallback> callbacks;
    private final Map<String, AssignedExperiment> assigned;
    private volatile GlobalContext globalContext;

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
        synchronized (GrowthBookClient.class) {

            if (repository != null) {
                return repository.getInitialized().get();
            }

            boolean isReady = false;
            try {

                GbCacheManager cm = this.options.getCacheManager() != null
                        ? this.options.getCacheManager()
                        : CacheManagerFactory.create(this.options.getCacheMode(), this.options.getCacheDirectory()
                );

                repository = GBFeaturesRepository.builder()
                        .apiHost(this.options.getApiHost())
                        .clientKey(this.options.getClientKey())
                        .decryptionKey(this.options.getDecryptionKey())
                        .refreshStrategy(this.options.getRefreshStrategy())
                        .swrTtlSeconds(this.options.getSwrTtlSeconds())
                        .isCacheDisabled(this.options.getIsCacheDisabled() || this.options.getCacheMode() == CacheMode.NONE)
                        .cacheManager(cm)
                        // if we don't want to pre-fetch for remote eval we can delete this line
                        .requestBodyForRemoteEval(configurePayloadForRemoteEval(this.options))
                        .build();

                // Add featureRefreshCallback
                repository.onFeaturesRefresh(this.options.getFeatureRefreshCallback());

                // Add a callback to refresh the global context
                repository.onFeaturesRefresh(this.refreshGlobalContext());

                try {
                    repository.initialize();
                } catch (FeatureFetchException e) {
                    log.error("Failed to initialize features repository", e);
                    throw new RuntimeException(e);
                }

                // instantiate a global context that holds features & savedGroups.
                this.globalContext = GlobalContext.builder()
                        .features(repository.getParsedFeatures().get())
                        .savedGroups(repository.getParsedSavedGroups().get())
                        .enabled(this.options.getEnabled())
                        .qaMode(this.options.getIsQaMode())
                        .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                        .forcedVariations(this.options.getGlobalForcedVariationsMap())
                        .build();

                isReady = repository.getInitialized().get();
                log.info("GrowthBookClient initialized repository and registered feature refresh callbacks.");

            } catch (Exception e) {
                log.error("Failed to initialize growthbook instance", e);
            }
            return isReady;
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

    public void refreshFeature() {
        try {
            repository.fetchFeatures();
        } catch (FeatureFetchException e) {
            log.error("Refreshing wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    public void refreshForRemoteEval(RequestBodyForRemoteEval requestBodyForRemoteEval) {
        try {
            repository.fetchForRemoteEval(requestBodyForRemoteEval);
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

    public void shutdown() {
        synchronized (GrowthBookClient.class) {
            if (repository != null) {
                repository.shutdown();
                repository = null;
                log.info("Repository shut down");
            }
        }
    }

    private FeatureRefreshCallback refreshGlobalContext() {
        return new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                GBFeaturesRepository repo = repository;
                if (repo == null) return;
                // refer the global context with latest features & saved groups
                if (globalContext != null) {
                    globalContext.setFeatures(repo.getParsedFeatures().get());
                    globalContext.setSavedGroups(repo.getParsedSavedGroups().get());
                } else {
                    // TBD:M This should never happen! Just to be cautious about race conditions at the time of initialization
                    globalContext = GlobalContext.builder()
                            .features(repo.getParsedFeatures().get())
                            .savedGroups(repo.getParsedSavedGroups().get())
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
        return new EvaluationContext(this.globalContext, updatedUserContext, new EvaluationContext.StackContext(), this.options);
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
