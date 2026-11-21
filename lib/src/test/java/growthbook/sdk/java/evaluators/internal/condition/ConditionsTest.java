package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionsTest {

    @Test
    @DisplayName("Matches when the condition definitely matches")
    void matchesDefiniteMatch() {
        // Given
        JsonObject condition = json("{\"country\": {\"$in\": [\"AA\", \"BB\"]}}");

        // When
        boolean matched = Conditions.matches(json("{\"country\": \"BB\"}"), condition, null);

        // Then
        assertTrue(matched);
    }

    @Test
    @DisplayName("Does not match when the condition fails")
    void doesNotMatchFailingCondition() {
        // Given
        JsonObject condition = json("{\"country\": {\"$in\": [\"AA\", \"BB\"]}}");

        // When
        boolean matched = Conditions.matches(json("{\"country\": \"CC\"}"), condition, null);

        // Then
        assertFalse(matched);
    }

    @Test
    @DisplayName("Does not match when the result is unknown")
    void doesNotMatchUnknownResult() {
        // Given
        JsonObject condition = json("{\"country\": {\"$foo\": 1}}");

        // When
        boolean matched = Conditions.matches(json("{\"country\": \"AA\"}"), condition, null);

        // Then
        assertFalse(matched);
    }

    @Test
    @DisplayName("Does not match, and does not throw, when evaluation reaches a malformed part")
    void doesNotMatchMalformedCondition() {
        // Given
        JsonObject condition = json("{\"a\": 1, \"$or\": \"not-an-array\"}");

        // When
        boolean matched = Conditions.matches(json("{\"a\": 1}"), condition, null);

        // Then
        assertFalse(matched);
    }

    @Test
    @DisplayName("Matches a negated unknown result as not matching")
    void doesNotMatchNegatedUnknownResult() {
        // Given
        JsonObject condition = json("{\"$not\": {\"country\": {\"$foo\": 1}}}");

        // When
        boolean matched = Conditions.matches(json("{\"country\": \"AA\"}"), condition, null);

        // Then
        assertFalse(matched);
    }

    @Test
    @DisplayName("Evaluates null attributes as having no attributes")
    void evaluatesNullAttributesAsEmpty() {
        // Given
        JsonObject condition = json("{\"country\": {\"$exists\": false}}");

        // When
        boolean matched = Conditions.matches(null, condition, null);

        // Then
        assertTrue(matched);
    }

    @Test
    @DisplayName("Resolves saved group references against the given saved groups")
    void resolvesSavedGroups() {
        // Given
        JsonObject condition = json("{\"id\": {\"$inGroup\": \"testers\"}}");
        JsonObject savedGroups = json("{\"testers\": [\"u1\"]}");

        // When
        boolean withGroups = Conditions.matches(json("{\"id\": \"u1\"}"), condition, savedGroups);
        boolean withoutGroups = Conditions.matches(json("{\"id\": \"u1\"}"), condition, null);

        // Then
        assertTrue(withGroups);
        assertFalse(withoutGroups);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
