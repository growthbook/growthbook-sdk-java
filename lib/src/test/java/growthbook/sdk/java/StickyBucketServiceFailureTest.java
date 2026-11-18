package growthbook.sdk.java;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickyBucketServiceFailureTest {

    private static final String EXPERIMENT_FEATURE =
            "{\"exp-flag\":{\"defaultValue\":\"control\",\"rules\":[{"
                    + "\"key\":\"exp-flag\","
                    + "\"variations\":[\"control\",\"treatment\"],"
                    + "\"weights\":[0.5,0.5],"
                    + "\"meta\":[{\"key\":\"0\"},{\"key\":\"1\"}],"
                    + "\"hashAttribute\":\"id\"}]}}";

    @Test
    @DisplayName("Verify: a failing sticky bucket service does not break construction or evaluation")
    void stickyBucketServiceFailureFallsBackToNormalBucketing() {
        // Given a sticky bucket service that throws on every call (e.g. Redis is down)
        FailingStickyBucketService service = new FailingStickyBucketService();
        GBContext context = GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson("{\"id\":\"user-1\"}")
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Arrays.asList("id"))
                .build();

        // When & Then — the load on construction is caught, so this must not throw
        GrowthBook subject = assertDoesNotThrow(() -> new GrowthBook(context));

        // And evaluating an experiment flag falls back to normal bucketing
        FeatureResult<Object> result = assertDoesNotThrow(() -> subject.evalFeature("exp-flag", Object.class));
        assertNotNull(result);
        assertNotNull(result.getValue());

        // The load was attempted and swallowed; nothing bucketed without the stored documents is saved
        assertTrue(service.loads.get() > 0, "the load path should have been attempted");
        assertEquals(0, service.saves.get(), "an assignment made after a failed load must not be saved");
    }

    @Test
    @DisplayName("Verify: a failed load never replaces the variation the store already holds")
    void failedLoadLeavesTheStoredAssignmentUntouched() {
        // Given a store holding the variation the user is NOT hashed into
        GrowthBook plain = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson("{\"id\":\"user-1\"}")
                .build());
        String hashed = (String) plain.evalFeature("exp-flag", Object.class).getValue();
        String storedKey = "control".equals(hashed) ? "1" : "0";
        String storedValue = "control".equals(hashed) ? "treatment" : "control";

        Map<String, String> assignments = new HashMap<>();
        assignments.put("exp-flag__0", storedKey);
        FlakyStickyBucketService service = new FlakyStickyBucketService();
        service.delegate.saveAssignments(new StickyAssignmentsDocument("id", "user-1", assignments));

        // When the load fails and an experiment flag is evaluated
        service.failLoads = true;
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson("{\"id\":\"user-1\"}")
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Arrays.asList("id"))
                .build());
        subject.evalFeature("exp-flag", Object.class);

        // Then the stored variation is unchanged
        assertEquals(storedKey,
                service.delegate.getAssignments("id", "user-1").getAssignments().get("exp-flag__0"));

        // And once the store loads again, the user gets the stored variation and saving resumes
        service.failLoads = false;
        subject.setAttributes("{\"id\":\"user-1\"}");
        assertEquals(storedValue, subject.evalFeature("exp-flag", Object.class).getValue());
    }

    @Test
    @DisplayName("Verify: a refresh that throws after a failed load does not re-enable saving")
    void aRefreshThatThrowsAfterAFailedLoadStillSavesNothing() {
        // Given a stored assignment and a load that failed at construction
        Map<String, String> assignments = new HashMap<>();
        assignments.put("exp-flag__0", "1");
        FlakyStickyBucketService service = new FlakyStickyBucketService();
        service.delegate.saveAssignments(new StickyAssignmentsDocument("id", "user-1", assignments));
        service.failLoads = true;
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson("{\"id\":\"user-1\"}")
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Arrays.asList("id"))
                .build());

        // When a features refresh throws before it reaches the store, and an experiment is evaluated
        assertThrows(RuntimeException.class, () -> subject.featuresAPIModelSuccessfully("{not json"));
        subject.evalFeature("exp-flag", Object.class);

        // Then the stored document is exactly what it was
        assertEquals(assignments, service.delegate.getAssignments("id", "user-1").getAssignments());
    }

    /** In-memory service whose loads can be made to fail, as when the store is briefly unreachable. */
    private static final class FlakyStickyBucketService implements StickyBucketService {
        final InMemoryStickyBucketServiceImpl delegate = new InMemoryStickyBucketServiceImpl();
        volatile boolean failLoads;

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            return delegate.getAssignments(attributeName, attributeValue);
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            delegate.saveAssignments(doc);
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            if (failLoads) {
                throw new RuntimeException("sticky store unavailable");
            }
            return delegate.getAllAssignments(attributes);
        }
    }

    /** Simulates a sticky bucket service backed by an unreachable store. */
    private static final class FailingStickyBucketService implements StickyBucketService {
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger saves = new AtomicInteger();

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            loads.incrementAndGet();
            throw new RuntimeException("sticky store unavailable");
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            saves.incrementAndGet();
            throw new RuntimeException("sticky store unavailable");
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            loads.incrementAndGet();
            throw new RuntimeException("sticky store unavailable");
        }
    }
}
