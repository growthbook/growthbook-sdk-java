package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.CondPruner;
import growthbook.sdk.java.evaluators.internal.condition.JsonValues;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps each saved group whose id appears as a string anywhere in the reduced payload, with
 * condition groups reduced. This needs no list of the operators that reference groups.
 *
 * <p>A group keeps its condition as written when it is evaluated against something other than
 * the user — each item in {@code $elemMatch}, or the parent's value in a prerequisite — when a
 * group like that references it, or when it is on a reference cycle. Every walk uses an explicit
 * stack, so a long chain of group references can't overflow the call stack.
 */
final class SavedGroupReducer {

    private final JsonObject attributes;
    private final JsonObject savedGroups;
    private final Set<String> asWritten = new HashSet<>();

    SavedGroupReducer(JsonObject savedGroups, JsonObject attributes) {
        this.savedGroups = savedGroups;
        this.attributes = attributes;
    }

    /**
     * @param reduced the reduced features, experiments and bandits
     * @return the saved groups they reference, in the order a recursive walk meets them
     */
    JsonObject reduce(JsonElement reduced) {
        JsonObject groups = new JsonObject();
        if (savedGroups.isEmpty()) {
            return groups;
        }
        markAsWritten(reduced);
        Set<String> onCycle = GroupCycles.find(savedGroups);
        for (Map.Entry<String, JsonElement> entry : keptGroups(reduced, onCycle).entrySet()) {
            groups.add(entry.getKey(), entry.getValue());
        }
        return groups;
    }

    /**
     * Whether a saved group's contents can reference other groups. A list's values (or a legacy
     * bare array's) are attribute values, never group ids.
     */
    static boolean canReference(JsonElement entry) {
        if (!entry.isJsonObject()) {
            return false;
        }
        JsonElement type = entry.getAsJsonObject().get("type");
        return !(JsonValues.isString(type) && type.getAsString().equals("list"));
    }

    /**
     * The saved group ids that appear as strings anywhere in a value.
     */
    static List<String> groupIdsIn(JsonElement root, JsonObject savedGroups) {
        List<String> found = new ArrayList<>();
        Deque<JsonElement> values = new ArrayDeque<>();
        values.push(root);
        while (!values.isEmpty()) {
            JsonElement value = values.pop();
            if (JsonValues.isString(value)) {
                if (savedGroups.has(value.getAsString())) {
                    found.add(value.getAsString());
                }
            } else {
                pushChildren(value, values);
            }
        }
        return found;
    }

    private void markAsWritten(JsonElement root) {
        Set<String> seenFromUser = new HashSet<>();
        Deque<Visit> visits = new ArrayDeque<>();
        visits.push(new Visit(root, true));
        while (!visits.isEmpty()) {
            Visit visit = visits.pop();
            if (JsonValues.isString(visit.value)) {
                markGroup(visit.value.getAsString(), visit.againstUser, seenFromUser, visits);
            } else if (visit.value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : visit.value.getAsJsonObject().entrySet()) {
                    visits.push(new Visit(entry.getValue(), visit.againstUser && evaluatesAgainstUser(entry.getKey())));
                }
            } else if (visit.value.isJsonArray()) {
                for (JsonElement element : visit.value.getAsJsonArray()) {
                    visits.push(new Visit(element, visit.againstUser));
                }
            }
        }
    }

    private void markGroup(String id, boolean againstUser, Set<String> seenFromUser, Deque<Visit> visits) {
        if (!savedGroups.has(id) || asWritten.contains(id) || (againstUser && seenFromUser.contains(id))) {
            return;
        }
        (againstUser ? seenFromUser : asWritten).add(id);
        if (canReference(savedGroups.get(id))) {
            visits.push(new Visit(savedGroups.get(id), againstUser));
        }
    }

    private static boolean evaluatesAgainstUser(String key) {
        return !key.equals("$elemMatch") && !key.equals(PayloadJson.PARENT_CONDITIONS);
    }

    private Map<String, JsonElement> keptGroups(JsonElement root, Set<String> onCycle) {
        Map<String, JsonElement> groups = new LinkedHashMap<>();
        Deque<JsonElement> values = new ArrayDeque<>();
        values.push(root);
        while (!values.isEmpty()) {
            JsonElement value = values.pop();
            if (!JsonValues.isString(value)) {
                pushChildrenInOrder(value, values);
                continue;
            }
            String id = value.getAsString();
            if (savedGroups.has(id) && !groups.containsKey(id)) {
                JsonElement kept = keptGroup(id, onCycle);
                groups.put(id, kept);
                if (canReference(savedGroups.get(id))) {
                    values.push(kept);
                }
            }
        }
        return groups;
    }

    private JsonElement keptGroup(String id, Set<String> onCycle) {
        JsonElement entry = savedGroups.get(id);
        if (asWritten.contains(id) || onCycle.contains(id) || !entry.isJsonObject()) {
            return entry;
        }
        JsonElement condition = CondPruner.pruneSavedGroup(attributes, id, savedGroups);
        if (condition == null || !condition.isJsonObject()) {
            return entry;
        }
        JsonObject reduced = PayloadJson.shallowCopy(entry.getAsJsonObject());
        reduced.add(PayloadJson.CONDITION, condition);
        return reduced;
    }

    private static void pushChildren(JsonElement value, Deque<JsonElement> values) {
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> values.push(entry.getValue()));
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(values::push);
        }
    }

    /**
     * Pushes the children reversed, so they are popped in order, as a recursive walk visits them.
     */
    private static void pushChildrenInOrder(JsonElement value, Deque<JsonElement> values) {
        List<JsonElement> children = new ArrayList<>();
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> children.add(entry.getValue()));
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(children::add);
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            values.push(children.get(i));
        }
    }

    /**
     * A value to walk, and whether it is evaluated against the user.
     */
    private static final class Visit {
        private final JsonElement value;
        private final boolean againstUser;

        Visit(JsonElement value, boolean againstUser) {
            this.value = value;
            this.againstUser = againstUser;
        }
    }
}
