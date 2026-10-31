package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the condition groups that can reach themselves through saved-group references. A group on
 * a cycle can't be reduced on its own: its result depends on which group the evaluation entered
 * the cycle from.
 *
 * <p>Tarjan's strongly connected components algorithm, run iteratively so a long chain of
 * references can't overflow the call stack. Each group's references are collected once.
 */
final class GroupCycles {

    private final Map<String, List<String>> references;
    private final Set<String> onCycle = new HashSet<>();
    private final Deque<String> frames = new ArrayDeque<>();
    private final Set<String> inComponent = new HashSet<>();
    private final Map<String, Integer> low = new HashMap<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final Deque<String> component = new ArrayDeque<>();
    private final Deque<Integer> positions = new ArrayDeque<>();

    private GroupCycles(Map<String, List<String>> references) {
        this.references = references;
    }

    /**
     * @param savedGroups the payload's saved groups
     * @return the ids of the groups on a cycle, including one that references itself
     */
    static Set<String> find(JsonObject savedGroups) {
        Map<String, List<String>> references = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : savedGroups.entrySet()) {
            references.put(entry.getKey(), SavedGroupReducer.canReference(entry.getValue())
                    ? SavedGroupReducer.groupIdsIn(entry.getValue(), savedGroups)
                    : Collections.emptyList());
        }
        GroupCycles cycles = new GroupCycles(references);
        for (String start : references.keySet()) {
            if (!cycles.index.containsKey(start)) {
                cycles.walkFrom(start);
            }
        }
        return cycles.onCycle;
    }

    /**
     * Each frame is a group and the position of the next reference to follow from it.
     */
    private void walkFrom(String start) {
        open(start);
        while (!frames.isEmpty()) {
            String id = frames.peek();
            int position = positions.pop();
            List<String> next = references.get(id);
            if (position < next.size()) {
                positions.push(position + 1);
                follow(id, next.get(position));
            } else {
                frames.pop();
                close(id, frames.peek());
            }
        }
    }

    private void open(String id) {
        index.put(id, index.size());
        low.put(id, index.get(id));
        component.push(id);
        inComponent.add(id);
        frames.push(id);
        positions.push(0);
    }

    private void follow(String from, String to) {
        if (to.equals(from)) {
            onCycle.add(from);
        }
        if (!index.containsKey(to)) {
            open(to);
        } else if (inComponent.contains(to)) {
            low.put(from, Math.min(low.get(from), index.get(to)));
        }
    }

    private void close(String id, @Nullable String parent) {
        if (parent != null) {
            low.put(parent, Math.min(low.get(parent), low.get(id)));
        }
        if (low.get(id).equals(index.get(id))) {
            popComponent(id);
        }
    }

    private void popComponent(String root) {
        List<String> members = new ArrayList<>();
        String member;
        do {
            member = component.pop();
            inComponent.remove(member);
            members.add(member);
        } while (!member.equals(root));
        if (members.size() > 1) {
            onCycle.addAll(members);
        }
    }
}
