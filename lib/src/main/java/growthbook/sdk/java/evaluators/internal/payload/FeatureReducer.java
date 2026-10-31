package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.JsonValues;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the features to keep: those the filters match, with their rules reduced, plus the
 * prerequisites the client evaluates for them, even when the filters drop those. With
 * {@code filterPrerequisites}, only the filters decide, except for holdouts, which aren't app
 * features and are always kept.
 */
final class FeatureReducer {

    private static final String HOLDOUT_PREFIX = "$holdout:";

    private final JsonObject allFeatures;
    private final PayloadFilter filter;
    private final ConditionReducer conditions;
    private final boolean filterPrerequisites;
    private final Map<String, JsonElement> kept = new LinkedHashMap<>();

    FeatureReducer(JsonObject allFeatures, PayloadFilter filter, ConditionReducer conditions, boolean filterPrerequisites) {
        this.allFeatures = allFeatures;
        this.filter = filter;
        this.conditions = conditions;
        this.filterPrerequisites = filterPrerequisites;
    }

    void addMatching() {
        for (Map.Entry<String, JsonElement> entry : allFeatures.entrySet()) {
            if (filter.matches(PayloadJson.metadataOf(entry.getValue()))) {
                add(entry.getKey());
            }
        }
    }

    /**
     * Adds the prerequisites of a rule or an auto experiment.
     */
    void addParents(JsonElement item) {
        for (String id : parentIds(item)) {
            add(id);
        }
    }

    /**
     * @return the kept features, in the order they were added
     */
    JsonObject features() {
        JsonObject features = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : kept.entrySet()) {
            features.add(entry.getKey(), entry.getValue());
        }
        return features;
    }

    /**
     * Adds a feature and, depth first, the prerequisites of its kept rules. Walks with an explicit
     * stack, so a long chain of prerequisites can't overflow the call stack; a feature is stored
     * before its prerequisites are visited, so a prerequisite cycle ends.
     */
    private void add(String id) {
        Deque<String> pending = new ArrayDeque<>();
        pending.push(id);
        while (!pending.isEmpty()) {
            List<String> parents = keep(pending.pop());
            for (int i = parents.size() - 1; i >= 0; i--) {
                pending.push(parents.get(i));
            }
        }
    }

    /**
     * @return the prerequisites of the feature's kept rules, in order; none when the feature is
     * absent or already kept
     */
    private List<String> keep(String id) {
        JsonElement feature = allFeatures.get(id);
        if (!JsonValues.isTruthy(feature) || kept.containsKey(id)) {
            return Collections.emptyList();
        }
        JsonArray rules = PayloadJson.arrayField(feature, PayloadJson.RULES);
        if (rules == null) {
            kept.put(id, feature);
            return Collections.emptyList();
        }
        JsonArray keptRules = reduceRules(feature, rules);
        JsonObject reduced = PayloadJson.shallowCopy(feature.getAsJsonObject());
        reduced.add(PayloadJson.RULES, keptRules);
        kept.put(id, reduced);
        List<String> parents = new ArrayList<>();
        for (JsonElement rule : keptRules) {
            parents.addAll(parentIds(rule));
        }
        return parents;
    }

    private List<String> parentIds(JsonElement item) {
        JsonArray parents = PayloadJson.arrayField(item, PayloadJson.PARENT_CONDITIONS);
        if (parents == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement parent : parents) {
            String id = parent.isJsonObject() ? JsonValues.stringField(parent.getAsJsonObject(), "id") : null;
            if (id != null && (!filterPrerequisites || id.startsWith(HOLDOUT_PREFIX))) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Rules scoped to other projects aren't served. A prerequisite from another project keeps its
     * own rules, as on the server.
     */
    private JsonArray reduceRules(JsonElement feature, JsonArray rules) {
        boolean scoped = filter.inProjects(PayloadJson.metadataOf(feature));
        JsonArray keptRules = new JsonArray();
        for (JsonElement rule : rules) {
            JsonElement ruleMetadata = rule.isJsonObject() ? rule.getAsJsonObject().get(PayloadJson.METADATA) : null;
            if (!scoped || filter.ruleInProjects(ruleMetadata)) {
                keptRules.add(conditions.reduce(rule, true));
            }
        }
        return keptRules;
    }
}
