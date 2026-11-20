package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CondScopeTest {

    @Test
    @DisplayName("Uses no saved groups when none are given")
    void usesEmptySavedGroupsForNull() {
        // Given
        JsonObject savedGroups = null;

        // When
        CondScope scope = new CondScope(savedGroups);

        // Then
        assertEquals(new JsonObject(), scope.savedGroups);
        assertEquals(Collections.emptySet(), scope.visited);
    }

    @Test
    @DisplayName("Keeps the given saved groups")
    void keepsGivenSavedGroups() {
        // Given
        JsonObject savedGroups = JsonParser.parseString("{\"g\": [\"u1\"]}").getAsJsonObject();

        // When
        CondScope scope = new CondScope(savedGroups);

        // Then
        assertSame(savedGroups, scope.savedGroups);
    }

    @Test
    @DisplayName("Gives a nested scope that records the visited group and leaves the outer scope unchanged")
    void visitingRecordsGroupInNewScope() {
        // Given
        JsonObject savedGroups = JsonParser.parseString("{\"g\": [\"u1\"]}").getAsJsonObject();
        CondScope outer = new CondScope(savedGroups);

        // When
        CondScope inner = outer.visiting("a").visiting("b");

        // Then
        assertEquals(Collections.emptySet(), outer.visited);
        assertEquals(new HashSet<>(Arrays.asList("a", "b")), inner.visited);
        assertSame(savedGroups, inner.savedGroups);
    }

    @Test
    @DisplayName("Exposes visited groups as an unmodifiable set")
    void exposesUnmodifiableVisitedSet() {
        // Given
        CondScope scope = new CondScope(null).visiting("a");

        // When / Then
        assertThrows(UnsupportedOperationException.class, () -> scope.visited.add("b"));
    }
}
