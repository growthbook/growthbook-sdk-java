package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.*;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
public class GrowthBookMultiUser {

    private final Options options;
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluatorEvaluator;
    private static GBFeaturesRepository repository;
    private final ArrayList<ExperimentRunCallback> callbacks;

    public GrowthBookMultiUser() {
        this(Options.builder().build());
    }

    public GrowthBookMultiUser(Options opts) {
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

                try {
                    repository.initialize();
                } catch (FeatureFetchException e) {
                    log.error("Failed to initialize features repository", e);
                    throw new RuntimeException(e);
                }
            }

            // load features JSON
            //repository.getFeaturesJson();

        } catch (Exception e) {
            log.error("Failed to initialize growthbook instance", e);
        }
    }

    private GlobalContext getGlobalContext() {
        GlobalContext globalContext =  GlobalContext.builder()
            .featuresJson(repository.getFeaturesJson())
            .build();

        globalContext.loadEncryptedFeatures(this.options.getDecryptionKey());
        return globalContext;
    }

    private EvaluationContext getEvalContext(UserContext userContext) {
        return new EvaluationContext(getGlobalContext(), userContext, new EvaluationContext.StackContext(), this.options);
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
