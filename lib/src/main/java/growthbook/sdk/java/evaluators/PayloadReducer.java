package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.PayloadFilters;
import growthbook.sdk.java.model.ReducePayloadOptions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shrinks a features payload for a user whose attributes are only partly known, e.g. on a server
 * bootstrapping a client SDK. The reduced payload evaluates the same as the full one for anyone
 * who agrees with the known attributes.
 *
 * <p>Stateless and safe for concurrent use. Inputs are never modified.</p>
 */
public final class PayloadReducer {

    private static final ConditionEvaluator EVALUATOR = new ConditionEvaluator();

    private PayloadReducer() {
    }

    /**
     * Partially evaluates a condition when only some attributes are known. Attributes missing from
     * {@code attributes} are unknown, and so is any nested (dotted) path.
     *
     * @param attributes  the known attributes
     * @param condition   the condition to prune
     * @param savedGroups the payload's saved groups, or {@code null} for none
     * @return a boolean {@link JsonPrimitive} once the result can't depend on unknown attributes,
     * otherwise a {@link JsonObject} with the condition minus every branch that is already decided
     */
    public static JsonElement pruneCondition(@Nullable JsonObject attributes, JsonObject condition, @Nullable JsonObject savedGroups) {
        return EVALUATOR.pruneCondition(attributes != null ? attributes : new JsonObject(), condition, savedGroups);
    }

    /**
     * Reduces a decrypted features payload for one user.
     *
     * <p>Features and auto experiments are kept or dropped by the {@code include}/{@code exclude}
     * filters on their project, tag and custom-field metadata. Rule and experiment conditions are
     * reduced with {@link #pruneCondition}: a condition that always passes is removed, and one that
     * never passes becomes {@code {"$not": {}}} (a force rule's value becomes {@code null}). The
     * prerequisites of kept features stay, unless {@link ReducePayloadOptions.Prerequisites#FILTER}
     * is set. Saved groups and contextual bandits nothing references any more are dropped.
     *
     * @param payload    the decrypted payload ({@code features}, {@code experiments},
     *                   {@code savedGroups}, {@code contextualBandits}, ...)
     * @param attributes the known attributes, global and per-user merged; {@code null} for none
     * @param options    filters and prerequisite handling; {@code null} for defaults
     * @return a new payload: the input's other keys, with {@code features}, {@code experiments} and
     * {@code savedGroups} reduced, and {@code contextualBandits} reduced when the input had them
     */
    public static JsonObject reducePayload(JsonObject payload, @Nullable JsonObject attributes, @Nullable ReducePayloadOptions options) {
        return new Reduction(payload, attributes != null ? attributes : new JsonObject(),
                options != null ? options : new ReducePayloadOptions()).run();
    }

    private static final class Reduction {
        private final JsonObject payload;
        private final JsonObject attributes;
        private final JsonObject savedGroups;
        private final JsonObject allFeatures;
        private final PayloadFilters include;
        private final PayloadFilters exclude;
        private final boolean filterPrerequisites;
        private final Map<String, JsonElement> added = new LinkedHashMap<>();

        Reduction(JsonObject payload, JsonObject attributes, ReducePayloadOptions options) {
            this.payload = payload;
            this.attributes = attributes;
            this.savedGroups = objectOrEmpty(payload.get("savedGroups"));
            this.allFeatures = objectOrEmpty(payload.get("features"));
            this.include = options.getInclude() != null ? options.getInclude() : new PayloadFilters();
            this.exclude = options.getExclude() != null ? options.getExclude() : new PayloadFilters();
            this.filterPrerequisites = options.getPrerequisites() == ReducePayloadOptions.Prerequisites.FILTER;
        }

        JsonObject run() {
            for (Map.Entry<String, JsonElement> entry : allFeatures.entrySet()) {
                if (matches(metadataOf(entry.getValue()))) {
                    add(entry.getKey());
                }
            }

            JsonArray experiments = new JsonArray();
            JsonElement allExperiments = payload.get("experiments");
            if (allExperiments != null && allExperiments.isJsonArray()) {
                for (JsonElement experiment : allExperiments.getAsJsonArray()) {
                    if (matches(metadataOf(experiment))) {
                        experiments.add(prune(experiment));
                    }
                }
            }
            for (JsonElement experiment : experiments) {
                addParents(experiment);
            }

            JsonObject features = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : added.entrySet()) {
                features.add(entry.getKey(), entry.getValue());
            }

            JsonObject contextualBandits = reduceBandits(features, experiments);

            Map<String, JsonElement> groups = new LinkedHashMap<>();
            keepSavedGroups(features, groups);
            keepSavedGroups(experiments, groups);
            keepSavedGroups(contextualBandits, groups);
            JsonObject reducedGroups = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
                reducedGroups.add(entry.getKey(), entry.getValue());
            }

            JsonObject result = shallowCopy(payload);
            result.add("features", features);
            result.add("experiments", experiments);
            result.add("savedGroups", reducedGroups);
            // Only when there were any, as with the payload's other optional keys
            if (isTruthy(payload.get("contextualBandits"))) {
                result.add("contextualBandits", contextualBandits);
            } else {
                result.remove("contextualBandits");
            }
            return result.deepCopy();
        }

        /**
         * Adds a feature, then the prerequisites the client evaluates for it, even when the
         * filters drop them. With {@code FILTER}, only the filters decide, except for holdouts,
         * which aren't app features and are always kept.
         */
        private void add(String id) {
            JsonElement feature = allFeatures.get(id);
            if (!isTruthy(feature) || added.containsKey(id)) {
                return;
            }
            JsonElement rules = feature.isJsonObject() ? feature.getAsJsonObject().get("rules") : null;
            if (rules == null || !rules.isJsonArray()) {
                added.put(id, feature);
                return;
            }
            // Rules scoped to other projects aren't served. A prerequisite from another project
            // keeps its own, as on the server.
            boolean scoped = inProjects(metadataOf(feature));
            JsonArray keptRules = new JsonArray();
            for (JsonElement rule : rules.getAsJsonArray()) {
                if (!scoped || ruleInProjects(rule.isJsonObject() ? rule.getAsJsonObject().get("metadata") : null)) {
                    keptRules.add(prune(rule));
                }
            }
            JsonObject reduced = shallowCopy(feature.getAsJsonObject());
            reduced.add("rules", keptRules);
            // Stored before its parents are added, so a prerequisite cycle ends
            added.put(id, reduced);
            for (JsonElement rule : keptRules) {
                addParents(rule);
            }
        }

        private void addParents(JsonElement item) {
            if (!item.isJsonObject()) {
                return;
            }
            JsonElement parents = item.getAsJsonObject().get("parentConditions");
            if (parents == null || !parents.isJsonArray()) {
                return;
            }
            for (JsonElement parent : parents.getAsJsonArray()) {
                String id = parent.isJsonObject() ? ConditionEvaluator.stringField(parent.getAsJsonObject(), "id") : null;
                if (id != null && (!filterPrerequisites || id.startsWith("$holdout:"))) {
                    add(id);
                }
            }
        }

        /**
         * Reduces a rule or an auto experiment's condition.
         */
        private JsonElement prune(JsonElement item) {
            if (!item.isJsonObject()) {
                return item;
            }
            JsonObject reduced = shallowCopy(item.getAsJsonObject());
            JsonElement condition = reduced.get("condition");
            if (condition == null || !condition.isJsonObject()) {
                return reduced;
            }
            JsonElement pruned = pruneCondition(attributes, condition.getAsJsonObject(), savedGroups);
            if (ConditionEvaluator.isBoolean(pruned)) {
                if (pruned.getAsBoolean()) {
                    reduced.remove("condition");
                } else {
                    // Kept, since sticky buckets and forced variations skip an experiment's
                    // condition, and a rule's prerequisites are checked before it
                    JsonObject never = new JsonObject();
                    never.add("$not", new JsonObject());
                    reduced.add("condition", never);
                    // A force rule that never passes never returns its value
                    if (reduced.has("force")) {
                        reduced.add("force", JsonNull.INSTANCE);
                    }
                }
            } else {
                reduced.add("condition", pruned);
            }
            return reduced;
        }

        /**
         * The contextual bandits that kept rules and experiments use. A context whose condition
         * never passes can't be the first that does, so it goes.
         */
        private JsonObject reduceBandits(JsonObject features, JsonArray experiments) {
            JsonObject allBandits = objectOrEmpty(payload.get("contextualBandits"));
            List<JsonElement> items = new ArrayList<>();
            for (Map.Entry<String, JsonElement> feature : features.entrySet()) {
                JsonElement rules = feature.getValue().isJsonObject() ? feature.getValue().getAsJsonObject().get("rules") : null;
                if (rules != null && rules.isJsonArray()) {
                    for (JsonElement rule : rules.getAsJsonArray()) {
                        items.add(rule);
                    }
                }
            }
            for (JsonElement experiment : experiments) {
                items.add(experiment);
            }

            JsonObject bandits = new JsonObject();
            for (JsonElement item : items) {
                String ref = item.isJsonObject() ? ConditionEvaluator.stringField(item.getAsJsonObject(), "contextualBanditRef") : null;
                if (ref == null || ref.isEmpty() || bandits.has(ref) || !allBandits.has(ref) || !allBandits.get(ref).isJsonObject()) {
                    continue;
                }
                JsonObject bandit = shallowCopy(allBandits.get(ref).getAsJsonObject());
                JsonArray contexts = new JsonArray();
                JsonElement allContexts = bandit.get("contexts");
                if (allContexts != null && allContexts.isJsonArray()) {
                    for (JsonElement context : allContexts.getAsJsonArray()) {
                        JsonObject reducedContext = shallowCopy(context.getAsJsonObject());
                        JsonElement condition = reducedContext.get("condition");
                        JsonElement pruned = pruneCondition(attributes,
                                condition != null && condition.isJsonObject() ? condition.getAsJsonObject() : new JsonObject(),
                                savedGroups);
                        if (ConditionEvaluator.isBoolean(pruned) && !pruned.getAsBoolean()) {
                            continue;
                        }
                        reducedContext.add("condition", ConditionEvaluator.isBoolean(pruned) ? new JsonObject() : pruned);
                        contexts.add(reducedContext);
                    }
                }
                bandit.add("contexts", contexts);
                bandits.add(ref, bandit);
            }
            return bandits;
        }

        /**
         * Keeps each saved group whose id appears as a string anywhere, with condition groups
         * reduced. This needs no list of the operators that reference groups.
         */
        private void keepSavedGroups(@Nullable JsonElement value, Map<String, JsonElement> groups) {
            if (value == null) {
                return;
            }
            if (ConditionEvaluator.isString(value)) {
                String id = value.getAsString();
                if (!savedGroups.has(id) || groups.containsKey(id)) {
                    return;
                }
                JsonElement entry = savedGroups.get(id);
                JsonElement condition = EVALUATOR.pruneSavedGroup(attributes, id, savedGroups);
                JsonElement kept = entry;
                if (condition != null && condition.isJsonObject() && entry.isJsonObject()) {
                    JsonObject reduced = shallowCopy(entry.getAsJsonObject());
                    reduced.add("condition", condition);
                    kept = reduced;
                }
                groups.put(id, kept);
                keepSavedGroups(kept, groups);
            } else if (value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    keepSavedGroups(entry.getValue(), groups);
                }
            } else if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    keepSavedGroups(element, groups);
                }
            }
        }

        /**
         * Kept when it matches every {@code include} filter and no {@code exclude} filter. Only
         * listed projects are excluded, so one in all projects stays.
         */
        private boolean matches(JsonObject metadata) {
            if (!inProjects(metadata)) {
                return false;
            }
            if (include.getTags() != null && !has(metadata.get("tags"), include.getTags())) {
                return false;
            }
            if (include.getCustomFields() != null) {
                for (Map.Entry<String, List<String>> field : include.getCustomFields().entrySet()) {
                    if (!has(customField(metadata, field.getKey()), field.getValue())) {
                        return false;
                    }
                }
            }
            if (has(metadata.get("projects"), exclude.getProjects()) || has(metadata.get("tags"), exclude.getTags())) {
                return false;
            }
            if (exclude.getCustomFields() != null) {
                for (Map.Entry<String, List<String>> field : exclude.getCustomFields().entrySet()) {
                    if (has(customField(metadata, field.getKey()), field.getValue())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * A feature or experiment has to list one of the projects, or be in all of them. One
         * without projects is in none, like one without tags.
         */
        private boolean inProjects(JsonObject metadata) {
            return include.getProjects() == null
                    || isTruthy(metadata.get("allProjects"))
                    || has(metadata.get("projects"), include.getProjects());
        }

        /**
         * A rule without projects applies wherever its feature is delivered.
         */
        private boolean ruleInProjects(@Nullable JsonElement metadata) {
            if (include.getProjects() == null || !isTruthy(metadata) || !metadata.isJsonObject()) {
                return true;
            }
            JsonElement projects = metadata.getAsJsonObject().get("projects");
            return !isTruthy(projects) || has(projects, include.getProjects());
        }

        @Nullable
        private static JsonElement customField(JsonObject metadata, String key) {
            JsonElement fields = metadata.get("customFields");
            return fields != null && fields.isJsonObject() ? fields.getAsJsonObject().get(key) : null;
        }

        /**
         * Only a list counts, as with {@code $in}, and any one of its values is enough.
         */
        private static boolean has(@Nullable JsonElement actual, @Nullable List<String> want) {
            if (want == null) {
                return false;
            }
            JsonArray values = new JsonArray();
            for (String value : want) {
                values.add(value);
            }
            return ConditionEvaluator.isIn(actual, values, false);
        }

        private static JsonObject metadataOf(@Nullable JsonElement item) {
            JsonElement metadata = item != null && item.isJsonObject() ? item.getAsJsonObject().get("metadata") : null;
            return metadata != null && metadata.isJsonObject() ? metadata.getAsJsonObject() : new JsonObject();
        }
    }

    private static JsonObject objectOrEmpty(@Nullable JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject shallowCopy(JsonObject source) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            copy.add(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /**
     * JavaScript truthiness of a JSON value: absent, null, false, 0 and "" are falsy.
     */
    private static boolean isTruthy(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                double number = primitive.getAsDouble();
                return number != 0 && !Double.isNaN(number);
            }
            return !primitive.getAsString().isEmpty();
        }
        return true;
    }
}
