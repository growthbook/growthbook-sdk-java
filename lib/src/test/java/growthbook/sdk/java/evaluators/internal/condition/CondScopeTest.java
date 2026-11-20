package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(scope.visited.isEmpty());
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
        assertTrue(outer.visited.isEmpty());
        assertEquals(2, inner.visited.size());
        assertTrue(inner.visited.contains("a"));
        assertTrue(inner.visited.contains("b"));
        assertSame(savedGroups, inner.savedGroups);
    }

    @Test
    @DisplayName("Exposes visited groups as an unmodifiable set")
    void exposesUnmodifiableVisitedSet() {
        // Given
        CondScope scope = new CondScope(null).visiting("a");

        // When
        Runnable mutation = () -> scope.visited.add("b");

        // Then
        assertThrows(UnsupportedOperationException.class, mutation::run);
    }
}
