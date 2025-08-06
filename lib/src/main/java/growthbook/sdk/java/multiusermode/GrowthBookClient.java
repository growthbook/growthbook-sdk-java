package growthbook.sdk.java.multiusermode;

import java.util.*;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private static GBFeaturesRepository repository;
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
        this.callbacks = new ArrayList<>();
    }

    public boolean initialize() {
        boolean isReady = false;
        try {

            if (repository == null) {
                repository = GBFeaturesRepository.builder()
                        .apiHost(this.options.getApiHost())
                        .clientKey(this.options.getClientKey())
                        .decryptionKey(this.options.getDecryptionKey())
                        .refreshStrategy(this.options.getRefreshStrategy())
                        .isCacheDisabled(this.options.getIsCacheDisabled())
                        .cacheManager(this.options.getCacheManager())
                        .inMemoryCache(this.options.getInMemoryCache())
                        .requestBodyForRemoteEval(configurePayloadForRemoteEval(this.options)) // if we don't want to pre-fetch for remote eval we can delete this line
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
                        .features(repository.getParsedFeatures())
                        .savedGroups(repository.getParsedSavedGroups())
                        .enabled(this.options.getEnabled())
                        .qaMode(this.options.getIsQaMode())
                        .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                        .forcedVariations(this.options.getGlobalForcedVariationsMap())
                        .build();

                isReady = repository.getInitialized();
            }
        } catch (Exception e) {
            log.error("Failed to initialize growthbook instance", e);
        }
        return isReady;
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

        fireSubscriptions(experiment, result);

        return result;
    }

    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
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
                // refer the global context with latest features & saved groups
                if (globalContext != null) {
                    Map<String, Feature<?>> features = GrowthBookJsonUtils.getInstance()
                            .gson.fromJson(featuresJson, new TypeToken<Map<String, Feature<?>>>() {
                            }.getType());
                    globalContext.setFeatures(features);
                } else {
                    // TBD:M This should never happen! Just to be cautious about race conditions at the time of initialization
                    globalContext = GlobalContext.builder()
                            .features(repository.getParsedFeatures())
                            .savedGroups(repository.getParsedSavedGroups())
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
        this.globalContext.setFeatures(repository.getParsedFeatures());

        HashMap<String, JsonElement> globalAttributes = null;
        if (this.options.getGlobalAttributes() != null) {
            globalAttributes = GrowthBookJsonUtils.getInstance().gson.fromJson(this.options.getGlobalAttributes(), HashMap.class);
        }

        HashMap<String, JsonElement> userAttributes = null;
        if (userContext.getAttributes() != null) {
            userAttributes = GrowthBookJsonUtils.getInstance().gson.fromJson(userContext.getAttributes(), HashMap.class);
        }

        if (globalAttributes != null) {
            if (userAttributes != null) {
                globalAttributes.putAll(userAttributes);
            }
        } else {
            globalAttributes = userAttributes;
        }

        String attributesJson = GrowthBookJsonUtils.getInstance().gson.toJson(globalAttributes);

        UserContext updatedUserContext = userContext.witAttributesJson(attributesJson);

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
