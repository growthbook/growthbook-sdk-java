package growthbook.sdk.java.multiusermode;

import java.util.*;
import growthbook.sdk.java.*;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import lombok.Getter;
import lombok.Setter;
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
    @Getter
    @Setter
    private Payload payload;

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
        this.payload = new Payload();
    }

    public boolean initialize() {
        boolean isReady = false;
        try {
            // load features, experiments, sticky bucket thing whatever!
            if (!this.options.getIsCacheDisabled()) {
                // enable cache? is there anything we could do here!
            }

            if (repository == null) {
                repository = GBFeaturesRepository.builder()
                        .apiHost(this.options.getApiHost())
                        .clientKey(this.options.getClientKey())
                        .decryptionKey(this.options.getDecryptionKey())
                        .refreshStrategy(this.options.getRefreshStrategy())
                        .payload(configurePayloadForRemoteEval(this.options))
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
                        .savedGroups(TransformationUtil.transformSavedGroups(repository.getSavedGroupsJson()))
                        .enabled(this.options.getEnabled())
                        .qaMode(this.options.getIsQaMode())
                        .forcedFeatureValues(this.options.getForcedFeatureValues())
                        .forcedVariations(this.options.getForcedVariationsMap())
                        .build();

                isReady = repository.getInitialized();
            }
        } catch (Exception e) {
            log.error("Failed to initialize growthbook instance", e);
        }
        return isReady;
    }

    private Payload configurePayloadForRemoteEval(Options options) {
        List<List<Object>> forceFeaturesForPayload = new ArrayList<>();
        if(options.getForcedFeatureValues() != null) {
            forceFeaturesForPayload = options.getForcedFeatureValues().entrySet().stream()
                    .map(entry -> Arrays.asList(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        }
        return new Payload(options.getAttributes(), forceFeaturesForPayload, options.getForcedVariationsMap(), options.getUrl());
    }

    private FeatureRefreshCallback refreshGlobalContext() {
        return new FeatureRefreshCallback() {
            @Override
            public void onRefresh(String featuresJson) {
                // refer the global context with latest features & saved groups
                if (globalContext != null) {
                    globalContext.setFeatures(TransformationUtil.transformFeatures(featuresJson));
                    globalContext.setSavedGroups(TransformationUtil.transformSavedGroups(repository.getSavedGroupsJson()));
                } else {
                    // TBD:M This should never happen! Just to be cautious about race conditions at the time of initialization
                    globalContext = GlobalContext.builder()
                            .features(TransformationUtil.transformFeatures(featuresJson))
                            .savedGroups(TransformationUtil.transformFeatures(repository.getSavedGroupsJson()))
                            .savedGroups(TransformationUtil.transformSavedGroups(repository.getSavedGroupsJson()))
                            .enabled(options.getEnabled())
                            .qaMode(options.getIsQaMode())
                            .forcedFeatureValues(options.getForcedFeatureValues())
                            .forcedVariations(options.getForcedVariationsMap())
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
 }
