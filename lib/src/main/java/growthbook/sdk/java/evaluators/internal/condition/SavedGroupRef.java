package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;

/**
 * What a {@code $savedGroup} reference ({@code {"id": ..., "attributeKey": ...}}) resolves to
 * against a scope: an already decided result, a list group, or a condition group.
 *
 * <p>Anything unrecognized matches nobody, and so does a cycle of references. A group type added
 * after this SDK was built is unknown. An {@code attributeKey} on the reference overrides a list
 * group's own attribute and must be a string, since falling back to the group's attribute would
 * check a different population; a condition group has no single attribute and ignores it.
 */
abstract class SavedGroupRef {

    private static final String FIELD_ID = "id";
    private static final String TYPE_LIST = "list";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_VALUES = "values";
    private static final String TYPE_CONDITION = "condition";
    private static final String FIELD_CONDITION = "condition";
    private static final String FIELD_ATTRIBUTE_KEY = "attributeKey";

    /**
     * @return whether the attributes are in the group, or {@code null} when that is unknown
     */
    @Nullable
    abstract Boolean eval(@Nullable JsonElement attributes);

    /**
     * @return the group's result as a boolean once it is decided by the known attributes; a
     * condition group's undecided condition minus every decided branch; or {@code null} while the
     * result is unknown
     */
    @Nullable
    abstract JsonElement prune(JsonObject attributes);

    static SavedGroupRef resolve(@Nullable JsonElement reference, CondScope scope) {
        if (reference == null || !reference.isJsonObject()) {
            return Decided.NO_MATCH;
        }
        JsonObject ref = reference.getAsJsonObject();
        String id = JsonValues.stringField(ref, FIELD_ID);
        JsonElement attributeKey = ref.get(FIELD_ATTRIBUTE_KEY);
        if (id == null || scope.visited.contains(id) || (attributeKey != null && !JsonValues.isString(attributeKey))) {
            return Decided.NO_MATCH;
        }

        JsonElement entry = scope.savedGroups.get(id);
        if (entry == null || !entry.isJsonObject()) {
            return Decided.NO_MATCH;
        }
        JsonObject group = entry.getAsJsonObject();
        String type = JsonValues.stringField(group, FIELD_TYPE);
        if (TYPE_LIST.equals(type)) {
            String key = attributeKey != null ? attributeKey.getAsString() : JsonValues.stringField(group, FIELD_ATTRIBUTE_KEY);
            return listGroup(key, group.get(FIELD_VALUES));
        }
        if (TYPE_CONDITION.equals(type)) {
            JsonElement condition = group.get(FIELD_CONDITION);
            return condition != null && condition.isJsonObject()
                    ? new ConditionGroup(condition.getAsJsonObject(), scope.visiting(id))
                    : Decided.NO_MATCH;
        }
        return Decided.UNKNOWN;
    }

    /**
     * The values of a saved group usable by {@code $inGroup}: an empty list for an unknown id, a
     * bare array, or a typed {@code list} entry's {@code values}; {@code null} for anything else.
     */
    @Nullable
    static JsonArray inGroupValues(@Nullable JsonElement group) {
        if (group == null) {
            return new JsonArray();
        }
        if (group.isJsonArray()) {
            return group.getAsJsonArray();
        }
        if (group.isJsonObject() && TYPE_LIST.equals(JsonValues.stringField(group.getAsJsonObject(), FIELD_TYPE))) {
            JsonElement values = group.getAsJsonObject().get(FIELD_VALUES);
            return values != null && values.isJsonArray() ? values.getAsJsonArray() : null;
        }
        return null;
    }

    /**
     * Whether a saved group entry has a type added after this SDK was built.
     */
    static boolean isNewGroupType(@Nullable JsonElement group) {
        if (group == null || !group.isJsonObject()) {
            return false;
        }
        String type = JsonValues.stringField(group.getAsJsonObject(), FIELD_TYPE);
        return !TYPE_LIST.equals(type) && !TYPE_CONDITION.equals(type);
    }

    private static SavedGroupRef listGroup(@Nullable String attributeKey, @Nullable JsonElement values) {
        if (attributeKey == null || values == null || !values.isJsonArray()) {
            return Decided.NO_MATCH;
        }
        return new ListGroup(attributeKey, values.getAsJsonArray());
    }

    private static final class Decided extends SavedGroupRef {
        static final SavedGroupRef NO_MATCH = new Decided(false);
        static final SavedGroupRef UNKNOWN = new Decided(null);

        @Nullable
        private final Boolean result;

        Decided(@Nullable Boolean result) {
            this.result = result;
        }

        @Nullable
        @Override
        Boolean eval(@Nullable JsonElement attributes) {
            return result;
        }

        @Nullable
        @Override
        JsonElement prune(JsonObject attributes) {
            return result != null ? new JsonPrimitive(result) : null;
        }
    }

    private static final class ListGroup extends SavedGroupRef {
        private final String attributeKey;
        private final JsonArray values;

        ListGroup(String attributeKey, JsonArray values) {
            this.attributeKey = attributeKey;
            this.values = values;
        }

        @Override
        Boolean eval(@Nullable JsonElement attributes) {
            return JsonValues.isIn(AttrPath.parse(attributeKey).attributeAt(attributes), values, false);
        }

        @Nullable
        @Override
        JsonElement prune(JsonObject attributes) {
            if (CondPruner.isUnknown(attributes, attributeKey)) {
                return null;
            }
            return new JsonPrimitive(JsonValues.isIn(AttrPath.parse(attributeKey).resolve(attributes), values, false));
        }
    }

    private static final class ConditionGroup extends SavedGroupRef {
        private final JsonObject condition;
        private final CondScope scope;

        ConditionGroup(JsonObject condition, CondScope scope) {
            this.condition = condition;
            this.scope = scope;
        }

        @Nullable
        @Override
        Boolean eval(@Nullable JsonElement attributes) {
            return CondParser.parse(condition).eval(attributes, scope);
        }

        @Override
        JsonElement prune(JsonObject attributes) {
            return CondPruner.pruneEntries(attributes, condition, scope);
        }
    }
}
