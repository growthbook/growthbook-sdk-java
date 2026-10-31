package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The saved groups a condition is evaluated against, and the group ids already being resolved,
 * to stop reference cycles. Immutable; a nested group gets a copy.
 */
public final class CondScope {

    final JsonObject savedGroups;
    final Set<String> visited;

    public CondScope(@Nullable JsonObject savedGroups) {
        this(savedGroups != null ? savedGroups : new JsonObject(), Collections.emptySet());
    }

    private CondScope(JsonObject savedGroups, Set<String> visited) {
        this.savedGroups = savedGroups;
        this.visited = visited;
    }

    CondScope visiting(String groupId) {
        Set<String> next = new HashSet<>(visited);
        next.add(groupId);
        return new CondScope(savedGroups, Collections.unmodifiableSet(next));
    }
}
