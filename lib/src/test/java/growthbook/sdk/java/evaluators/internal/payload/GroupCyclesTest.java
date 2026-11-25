package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class GroupCyclesTest {

    private static final int LONG_CHAIN = 10_000;

    @Test
    @DisplayName("Finds a condition group that references itself")
    void findsSelfReferencingGroup() {
        // Given
        JsonObject savedGroups = json("{'a': " + references("a") + ", 'b': " + references("a") + "}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(setOf("a"), onCycle);
    }

    @Test
    @DisplayName("Finds both groups of a two-group cycle")
    void findsTwoGroupCycle() {
        // Given
        JsonObject savedGroups = json("{'a': " + references("b") + ", 'b': " + references("a") + ", 'c': " + references("a") + "}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(setOf("a", "b"), onCycle);
    }

    @Test
    @DisplayName("Finds every group of a three-group cycle and none leading into it")
    void findsThreeGroupCycle() {
        // Given
        JsonObject savedGroups = json("{'entry': " + references("a") + ", 'a': " + references("b") + ", 'b': " + references("c")
                + ", 'c': " + references("a") + ", 'exit': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(setOf("a", "b", "c"), onCycle);
    }

    @Test
    @DisplayName("Finds separate cycles in the same saved groups")
    void findsSeparateCycles() {
        // Given
        JsonObject savedGroups = json("{'a': " + references("b") + ", 'b': " + references("a")
                + ", 'c': " + references("d") + ", 'd': " + references("c") + ", 'e': " + references("a", "c") + "}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(setOf("a", "b", "c", "d"), onCycle);
    }

    @Test
    @DisplayName("Finds no cycle in an acyclic chain of references")
    void findsNoCycleInAcyclicChain() {
        // Given
        JsonObject savedGroups = json("{'a': " + references("b") + ", 'b': " + references("c", "d") + ", 'c': " + references("d")
                + ", 'd': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(Collections.emptySet(), onCycle);
    }

    @Test
    @DisplayName("Never counts a list group's values as references")
    void listGroupsNeverReference() {
        // Given
        JsonObject savedGroups = json("{'self': {'type': 'list', 'attributeKey': 'id', 'values': ['self', 'cond']},"
                + " 'cond': " + references("self") + "}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(Collections.emptySet(), onCycle);
    }

    @Test
    @DisplayName("Never counts a legacy bare-array group's values as references")
    void bareArrayGroupsNeverReference() {
        // Given
        JsonObject savedGroups = json("{'legacy': ['legacy', 'cond'], 'cond': " + references("legacy") + "}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(Collections.emptySet(), onCycle);
    }

    @Test
    @DisplayName("Ignores references to groups that do not exist")
    void ignoresMissingGroups() {
        // Given
        JsonObject savedGroups = json("{'a': " + references("missing") + "}");

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(Collections.emptySet(), onCycle);
    }

    @Test
    @DisplayName("Finds no cycle when there are no saved groups")
    void findsNoCycleWithoutGroups() {
        // Given
        JsonObject savedGroups = new JsonObject();

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(Collections.emptySet(), onCycle);
    }

    @Test
    @DisplayName("Walks a very long acyclic chain of references without overflowing the stack")
    void walksLongAcyclicChain() {
        // Given
        JsonObject savedGroups = chain(LONG_CHAIN, false);

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(Collections.emptySet(), onCycle);
    }

    @Test
    @DisplayName("Finds every group of a very long cycle without overflowing the stack")
    void findsLongCycle() {
        // Given
        JsonObject savedGroups = chain(LONG_CHAIN, true);

        // When
        Set<String> onCycle = GroupCycles.find(savedGroups);

        // Then
        assertEquals(savedGroups.keySet(), onCycle);
    }

    private static JsonObject chain(int length, boolean closed) {
        JsonObject savedGroups = new JsonObject();
        for (int i = 0; i < length; i++) {
            String next = i + 1 < length ? "g" + (i + 1) : (closed ? "g0" : null);
            savedGroups.add("g" + i, next != null
                    ? json(references(next))
                    : json("{'type': 'list', 'attributeKey': 'id', 'values': ['v1']}"));
        }
        return savedGroups;
    }

    private static String references(String... ids) {
        StringBuilder or = new StringBuilder();
        for (String id : ids) {
            or.append(or.length() == 0 ? "" : ", ").append("{'$savedGroup': {'id': '").append(id).append("'}}");
        }
        return "{'type': 'condition', 'condition': {'$or': [" + or + "]}}";
    }

    private static Set<String> setOf(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
