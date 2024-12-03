package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.*;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
public class GrowthBookClient {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private static GBFeaturesRepository repository;
    private final ArrayList<ExperimentRunCallback> callbacks;
    private GlobalContext globalContext;

    public GrowthBookClient() {
        this(Options.builder().build());
    }

    public GrowthBookClient(Options opts) {
        this.options = opts == null ? Options.builder().build() : opts;

        this.featureEvaluator = new FeatureEvaluator();
        this.experimentEvaluatorEvaluator = new ExperimentEvaluator();
        this.callbacks = new ArrayList<>();
    }

    public void initialize() {
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
            }
        } catch (Exception e) {
            log.error("Failed to initialize growthbook instance", e);
        }
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

        this.callbacks.forEach(callback -> callback.onRun(result));

        return result;
    }

    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }
 }
