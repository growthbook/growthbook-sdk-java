package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Partially evaluates conditions when only some attributes are known, for payload reduction.
 * Attributes missing from the known attributes are unknown, and so is any nested path, since a
 * server may only have part of an object. An entry stays as written while its result is unknown,
 * including when it uses an operator this SDK doesn't support but the client's SDK may. Pure
 * functions.
 */
public final class CondPruner {

    private static final JsonPrimitive TRUE = new JsonPrimitive(true);
    private static final JsonPrimitive FALSE = new JsonPrimitive(false);

    private CondPruner() {
    }

    /**
     * @param attributes  the known attributes
     * @param condition   the condition to prune
     * @param savedGroups the saved groups references resolve against
     * @return {@code true} or {@code false} once the result can't depend on unknown attributes,
     * otherwise the condition minus every branch that is already decided. A condition that
     * throws, such as a malformed one or one whose chain of group references exhausts the stack,
     * is returned as written.
     */
    public static JsonElement pruneCondition(JsonObject attributes, JsonObject condition, @Nullable JsonObject savedGroups) {
        try {
            return pruneEntries(attributes, condition, new CondScope(savedGroups));
        } catch (RuntimeException | StackOverflowError e) {
            return condition;
        }
    }

    /**
     * @param attributes  the known attributes
     * @param groupId     the saved group to prune
     * @param savedGroups the saved groups
     * @return the group's result once decided; its condition minus every decided branch for a
     * condition group that isn't; or {@code null} while a list group's attribute is unknown, the
     * group's type is unknown, or its condition throws (it is then kept as written)
     */
    @Nullable
    public static JsonElement pruneSavedGroup(JsonObject attributes, String groupId, @Nullable JsonObject savedGroups) {
        JsonObject reference = new JsonObject();
        reference.addProperty("id", groupId);
        try {
            return SavedGroupRef.resolve(reference, new CondScope(savedGroups)).prune(attributes);
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
    }

    /**
     * @return a boolean primitive once decided, otherwise the undecided entries
     */
    static JsonElement pruneEntries(JsonObject attributes, JsonObject condition, CondScope scope) {
        JsonObject undecided = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            JsonElement pruned = pruneEntry(attributes, entry.getKey(), entry.getValue(), scope);
            Boolean decided = JsonValues.asBoolean(pruned);
            if (decided == null) {
                undecided.add(entry.getKey(), pruned != null ? pruned : entry.getValue());
            } else if (!decided) {
                return FALSE;
            }
        }
        return undecided.isEmpty() ? TRUE : undecided;
    }

    /**
     * @return a boolean primitive once decided, the rewritten entry, or {@code null} to keep the
     * entry as written
     */
    @Nullable
    private static JsonElement pruneEntry(JsonObject attributes, String key, JsonElement value, CondScope scope) {
        switch (key) {
            case "$or":
                return pruneList(attributes, value.getAsJsonArray(), scope, true);
            case "$and":
                return pruneList(attributes, value.getAsJsonArray(), scope, false);
            case "$nor":
                return negate(pruneList(attributes, value.getAsJsonArray(), scope, true));
            case "$not":
                return negate(pruneEntries(attributes, value.getAsJsonObject(), scope));
            case "$savedGroup":
                return decidedOnly(SavedGroupRef.resolve(value, scope).prune(attributes));
            default:
                return pruneField(attributes, key, value, scope);
        }
    }

    @Nullable
    private static JsonElement pruneField(JsonObject attributes, String path, JsonElement value, CondScope scope) {
        if (isUnknown(attributes, path)) {
            return null;
        }
        Boolean matched = CondParser.parseValue(value).test(AttrPath.parse(path).resolve(attributes), scope);
        return matched != null ? new JsonPrimitive(matched) : null;
    }

    /**
     * Prunes an {@code $or} or an {@code $and} list. One passing condition decides an {@code $or},
     * and one failing condition an {@code $and}; when every condition is decided without that,
     * the {@code $or} fails and the {@code $and} passes, except that an empty {@code $or} passes,
     * as in evaluation.
     *
     * @return the list's result once decided, otherwise the conditions still undecided
     */
    private static JsonElement pruneList(JsonObject attributes, JsonArray conditions, CondScope scope, boolean or) {
        JsonArray undecided = new JsonArray();
        for (JsonElement condition : conditions) {
            JsonElement pruned = pruneEntries(attributes, condition.getAsJsonObject(), scope);
            Boolean decided = JsonValues.asBoolean(pruned);
            if (decided == null) {
                undecided.add(pruned);
            } else if (decided == or) {
                return pruned;
            }
        }
        if (!undecided.isEmpty()) {
            return undecided;
        }
        return new JsonPrimitive(!or || conditions.isEmpty());
    }

    /**
     * Keeps a {@code $savedGroup} reference as written until it is decided, rather than inlining
     * the group's pruned condition.
     */
    @Nullable
    private static JsonElement decidedOnly(@Nullable JsonElement pruned) {
        return JsonValues.asBoolean(pruned) != null ? pruned : null;
    }

    private static JsonElement negate(JsonElement pruned) {
        Boolean decided = JsonValues.asBoolean(pruned);
        return decided != null ? new JsonPrimitive(!decided) : pruned;
    }

    static boolean isUnknown(JsonObject attributes, String path) {
        return path.contains(".") || !attributes.has(path);
    }
}
