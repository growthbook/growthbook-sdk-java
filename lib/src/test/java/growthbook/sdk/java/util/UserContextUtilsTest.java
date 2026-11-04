package growthbook.sdk.java.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextUtilsTest {

    @Test
    @DisplayName("Verify: merging user attributes leaves the global attributes unchanged")
    void mergingUserAttributesLeavesGlobalAttributesUnchanged() {
        // Given
        Options options = Options.builder().build();
        options.setGlobalAttributes("{\"app\":\"demo\"}");
        UserContext userA = userContext("{\"id\":\"A\",\"country\":\"FR\"}");

        // When
        UserContextUtils.mergeAttributesAndPreloadSticky(options, userA);

        // Then
        assertEquals(json("{\"app\":\"demo\"}"), options.getGlobalAttributes());
    }

    @Test
    @DisplayName("Verify: one user's attributes do not leak into a later user's merged attributes")
    void oneUsersAttributesDoNotLeakIntoALaterUser() {
        // Given
        Options options = Options.builder().build();
        options.setGlobalAttributes(null);
        UserContext userA = userContext("{\"id\":\"A\",\"country\":\"FR\"}");
        UserContext userB = userContext("{\"id\":\"B\"}");

        // When
        UserContextUtils.mergeAttributesAndPreloadSticky(options, userA);
        UserContext mergedB = UserContextUtils.mergeAttributesAndPreloadSticky(options, userB);

        // Then
        assertFalse(mergedB.getAttributes().has("country"));
        assertEquals(json("{\"id\":\"B\"}"), mergedB.getAttributes());
    }

    @Test
    @DisplayName("Verify: user attributes override global attributes with the same key")
    void userAttributesOverrideGlobalAttributes() {
        // Given
        Options options = Options.builder().build();
        options.setGlobalAttributes("{\"country\":\"US\",\"app\":\"demo\"}");
        UserContext user = userContext("{\"country\":\"FR\"}");

        // When
        UserContext merged = UserContextUtils.mergeAttributesAndPreloadSticky(options, user);

        // Then
        assertEquals(json("{\"country\":\"FR\",\"app\":\"demo\"}"), merged.getAttributes());
        assertEquals("US", options.getGlobalAttributes().get("country").getAsString());
    }

    @Test
    @DisplayName("Verify: absent global attributes and a null user context merge to empty attributes")
    void absentGlobalAttributesAndNullUserContextMergeToEmpty() {
        // Given
        Options options = Options.builder().build();

        // When
        UserContext merged = UserContextUtils.mergeAttributesAndPreloadSticky(options, null);

        // Then
        assertTrue(merged.getAttributes().entrySet().isEmpty());
    }

    private static UserContext userContext(String attributesJson) {
        return UserContext.builder().attributes(json(attributesJson)).build();
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
