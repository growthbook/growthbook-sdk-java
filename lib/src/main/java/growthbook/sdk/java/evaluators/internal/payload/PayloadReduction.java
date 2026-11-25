package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.JsonValues;
import growthbook.sdk.java.model.ReducePayloadOptions;

/**
 * Reduces a decrypted features payload for one user: features and auto experiments the filters
 * keep, with their conditions reduced and their prerequisites added, then the contextual bandits
 * and saved groups those still reference. Inputs are never modified.
 */
public final class PayloadReduction {

    private final JsonObject payload;
    private final JsonObject attributes;
    private final JsonObject savedGroups;
    private final ReducePayloadOptions options;
    private final PayloadFilter filter;
    private final ConditionReducer conditions;

    private PayloadReduction(JsonObject payload, JsonObject attributes, ReducePayloadOptions options) {
        this.payload = payload;
        this.attributes = attributes;
        this.savedGroups = PayloadJson.objectOrEmpty(payload.get(PayloadJson.SAVED_GROUPS));
        this.options = options;
        this.filter = new PayloadFilter(options.getInclude(), options.getExclude());
        this.conditions = new ConditionReducer(attributes, savedGroups);
    }

    /**
     * @param payload    the decrypted payload
     * @param attributes the known attributes
     * @param options    filters and prerequisite handling
     * @return a new payload: the input's other keys, with {@code features}, {@code experiments} and
     * {@code savedGroups} reduced, and {@code contextualBandits} reduced when the input had them
     */
    public static JsonObject reduce(JsonObject payload, JsonObject attributes, ReducePayloadOptions options) {
        return new PayloadReduction(payload, attributes, options).run();
    }

    private JsonObject run() {
        JsonArray experiments = reduceExperiments();
        JsonObject features = reduceFeatures(experiments);
        JsonObject bandits = new BanditReducer(PayloadJson.objectOrEmpty(payload.get(PayloadJson.CONTEXTUAL_BANDITS)), conditions)
                .reduce(features, experiments);
        JsonObject groups = new SavedGroupReducer(savedGroups, attributes).reduce(arrayOf(features, experiments, bandits));
        return assemble(features, experiments, groups, bandits);
    }

    private JsonArray reduceExperiments() {
        JsonArray experiments = new JsonArray();
        JsonArray allExperiments = PayloadJson.arrayField(payload, PayloadJson.EXPERIMENTS);
        if (allExperiments != null) {
            for (JsonElement experiment : allExperiments) {
                if (filter.matches(PayloadJson.metadataOf(experiment))) {
                    experiments.add(conditions.reduce(experiment, false));
                }
            }
        }
        return experiments;
    }

    /**
     * The matching features first, then the prerequisites of the kept experiments.
     */
    private JsonObject reduceFeatures(JsonArray experiments) {
        FeatureReducer features = new FeatureReducer(
                PayloadJson.objectOrEmpty(payload.get(PayloadJson.FEATURES)),
                filter,
                conditions,
                options.getPrerequisites() == ReducePayloadOptions.Prerequisites.FILTER);
        features.addMatching();
        experiments.forEach(features::addParents);
        return features.features();
    }

    /**
     * Contextual bandits stay only when the input had any, as with the payload's other optional keys.
     */
    private JsonObject assemble(JsonObject features, JsonArray experiments, JsonObject groups, JsonObject bandits) {
        JsonObject result = PayloadJson.shallowCopy(payload);
        result.add(PayloadJson.FEATURES, features);
        result.add(PayloadJson.EXPERIMENTS, experiments);
        result.add(PayloadJson.SAVED_GROUPS, groups);
        if (JsonValues.isTruthy(payload.get(PayloadJson.CONTEXTUAL_BANDITS))) {
            result.add(PayloadJson.CONTEXTUAL_BANDITS, bandits);
        } else {
            result.remove(PayloadJson.CONTEXTUAL_BANDITS);
        }
        return result.deepCopy();
    }

    private static JsonArray arrayOf(JsonElement... elements) {
        JsonArray array = new JsonArray();
        for (JsonElement element : elements) {
            array.add(element);
        }
        return array;
    }
}
