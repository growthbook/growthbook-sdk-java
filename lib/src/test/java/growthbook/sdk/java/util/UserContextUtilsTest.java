package growthbook.sdk.java.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextUtilsTest {

    @Test
    @DisplayName("Merging user attributes leaves the global attributes unchanged")
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
    @DisplayName("One user's attributes do not leak into a later user's merged attributes")
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
    @DisplayName("User attributes override global attributes with the same key")
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
    @DisplayName("Absent global attributes and a null user context merge to empty attributes")
    void absentGlobalAttributesAndNullUserContextMergeToEmpty() {
        // Given
        Options options = Options.builder().build();

        // When
        UserContext merged = UserContextUtils.mergeAttributesAndPreloadSticky(options, null);

        // Then
        assertTrue(merged.getAttributes().entrySet().isEmpty());
    }

    @Test
    @DisplayName("Preloads sticky assignments for the merged attributes formatted as hash values")
    void preloadsStickyAssignmentsForMergedAttributes() {
        // Given
        RecordingStickyBucketService service = new RecordingStickyBucketService();
        Options options = Options.builder().stickyBucketService(service).build();
        options.setGlobalAttributes("{\"deviceId\":\"device-1\"}");
        UserContext user = userContext("{\"id\":1.0,\"tags\":[\"a\"]}");

        // When
        UserContext merged = UserContextUtils.mergeAttributesAndPreloadSticky(options, user);

        // Then
        Map<String, String> expectedLookup = new HashMap<>();
        expectedLookup.put("id", "1");
        expectedLookup.put("deviceId", "device-1");
        assertEquals(expectedLookup, service.requestedAttributes);
        assertSame(service.documents, merged.getStickyBucketAssignmentDocs());
    }

    @Test
    @DisplayName("Keeps sticky assignments the caller already supplied without querying the service")
    void keepsCallerSuppliedStickyAssignments() {
        // Given
        RecordingStickyBucketService service = new RecordingStickyBucketService();
        Options options = Options.builder().stickyBucketService(service).build();
        Map<String, StickyAssignmentsDocument> supplied = Collections.singletonMap("id||user-1",
                new StickyAssignmentsDocument("id", "user-1", Collections.singletonMap("exp__0", "1")));
        UserContext user = UserContext.builder()
                .attributes(json("{\"id\":\"user-1\"}"))
                .stickyBucketAssignmentDocs(supplied)
                .build();

        // When
        UserContext merged = UserContextUtils.mergeAttributesAndPreloadSticky(options, user);

        // Then
        assertNull(service.requestedAttributes);
        assertSame(supplied, merged.getStickyBucketAssignmentDocs());
    }

    @Test
    @DisplayName("Continues without sticky assignments when the sticky bucket service throws")
    void continuesWithoutStickyAssignmentsWhenServiceThrows() {
        // Given
        RecordingStickyBucketService service = new RecordingStickyBucketService();
        service.failure = new IllegalStateException("store unavailable");
        Options options = Options.builder().stickyBucketService(service).build();
        UserContext user = userContext("{\"id\":\"user-1\"}");

        // When
        UserContext merged = UserContextUtils.mergeAttributesAndPreloadSticky(options, user);

        // Then
        assertNull(merged.getStickyBucketAssignmentDocs());
        assertEquals(json("{\"id\":\"user-1\"}"), merged.getAttributes());
    }

    private static UserContext userContext(String attributesJson) {
        return UserContext.builder().attributes(json(attributesJson)).build();
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static final class RecordingStickyBucketService implements StickyBucketService {
        private final Map<String, StickyAssignmentsDocument> documents = new HashMap<>();
        private Map<String, String> requestedAttributes;
        private RuntimeException failure;

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            return null;
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            requestedAttributes = new HashMap<>(attributes);
            if (failure != null) {
                throw failure;
            }
            return documents;
        }
    }
}
