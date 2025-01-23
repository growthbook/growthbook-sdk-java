package growthbook.sdk.java.multiusermode;

import java.util.*;
import growthbook.sdk.java.*;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private static GBFeaturesRepository repository;
    private final List<ExperimentRunCallback> callbacks;
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
        boolean isReady = false;
        try {

            if (repository == null) {
                repository = GBFeaturesRepository.builder()
                        .apiHost(this.options.getApiHost())
                        .clientKey(this.options.getClientKey())
                        .decryptionKey(this.options.getDecryptionKey())
                        .refreshStrategy(this.options.getRefreshStrategy())
                        .isCacheDisabled(this.options.getIsCacheDisabled())
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
                        .features(TransformationUtil.transformFeatures(repository.getFeaturesJson()))
                        .build();

                isReady = repository.getInitialized();
            }
        } catch (Exception e) {
            log.error("Failed to initialize growthbook instance", e);
        }
        return isReady;
    }

    private FeatureRefreshCallback refreshGlobalContext() {
        return new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                // refer the global context with latest features & saved groups
                if (globalContext != null) {
                    globalContext.setFeatures(TransformationUtil.transformFeatures(featuresJson));
                    globalContext.setSavedGroups(TransformationUtil.transformFeatures(repository.getSavedGroupsJson()));
                } else {
                    // TBD:M This should never happen! Just to be cautious about race conditions at the time of initialization
                    globalContext = GlobalContext.builder()
                            .features(TransformationUtil.transformFeatures(featuresJson))
                            .savedGroups(TransformationUtil.transformFeatures(repository.getSavedGroupsJson()))
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
        return new EvaluationContext(this.globalContext, userContext, new EvaluationContext.StackContext(), this.options);
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
                || !Objects.equals(prev.getExperimentResult().getInExperiment(), result.getInExperiment())
                || !Objects.equals(prev.getExperimentResult().getVariationId(), result.getVariationId())) {
            this.assigned.put(key, new AssignedExperiment<>(experiment, result));
            for (ExperimentRunCallback cb : this.callbacks) {
                try {
                    cb.onRun(experiment, result);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }
    }
 }
