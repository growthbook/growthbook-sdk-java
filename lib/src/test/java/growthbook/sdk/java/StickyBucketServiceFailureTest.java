package growthbook.sdk.java;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        // And evaluating an experiment flag (whose save also throws) falls back to normal bucketing
        FeatureResult<Object> result = assertDoesNotThrow(() -> subject.evalFeature("exp-flag", Object.class));
        assertNotNull(result);
        assertNotNull(result.getValue());

        // Both the load and save paths were exercised through the throwing service and swallowed
        assertTrue(service.loads.get() > 0, "the load path should have been attempted");
        assertTrue(service.saves.get() > 0, "the save path should have been attempted");
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
