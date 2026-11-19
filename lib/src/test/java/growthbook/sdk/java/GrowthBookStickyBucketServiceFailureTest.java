package growthbook.sdk.java;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookStickyBucketServiceFailureTest {

    private static final String EXPERIMENT_FEATURE = "{\"exp-flag\":{\"defaultValue\":\"control\",\"rules\":[{"
            + "\"key\":\"exp-flag\","
            + "\"variations\":[\"control\",\"treatment\"],"
            + "\"weights\":[0.5,0.5],"
            + "\"meta\":[{\"key\":\"0\"},{\"key\":\"1\"}],"
            + "\"hashAttribute\":\"id\"}]}}";
    private static final String ATTRIBUTES = "{\"id\":\"user-1\"}";

    @Test
    @DisplayName("Falls back to normal bucketing and saves nothing when the sticky bucket service throws on load")
    void fallsBackToNormalBucketingAndSavesNothingWhenStickyBucketLoadThrows() {
        // Given
        FailingStickyBucketService service = new FailingStickyBucketService();
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson(ATTRIBUTES)
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());
        GrowthBook withoutStickyBucketing = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson(ATTRIBUTES)
                .build());

        // When
        FeatureResult<Object> result = subject.evalFeature("exp-flag", Object.class);

        // Then
        FeatureResult<Object> expected = withoutStickyBucketing.evalFeature("exp-flag", Object.class);

        assertTrue(service.loads.get() > 0);
        assertEquals(0, service.saves.get());
        assertEquals(expected.getValue(), result.getValue());
        assertEquals(FeatureResultSource.EXPERIMENT, result.getSource());
        assertEquals(expected.getExperimentResult().getVariationId(), result.getExperimentResult().getVariationId());
    }

    @Test
    @DisplayName("Never replaces the variation the store already holds after a failed load")
    void failedLoadLeavesStoredAssignmentUntouched() {
        // Given
        GrowthBook plain = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson(ATTRIBUTES)
                .build());
        String hashed = (String) plain.evalFeature("exp-flag", Object.class).getValue();
        String storedKey = "control".equals(hashed) ? "1" : "0";
        String storedValue = "control".equals(hashed) ? "treatment" : "control";
        Map<String, String> assignments = new HashMap<>();
        assignments.put("exp-flag__0", storedKey);
        FlakyStickyBucketService service = new FlakyStickyBucketService();
        service.delegate.saveAssignments(new StickyAssignmentsDocument("id", "user-1", assignments));
        service.failLoads = true;
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson(ATTRIBUTES)
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());

        // When
        subject.evalFeature("exp-flag", Object.class);
        String storedAfterFailedLoad = service.delegate.getAssignments("id", "user-1").getAssignments().get("exp-flag__0");
        service.failLoads = false;
        subject.setAttributes(ATTRIBUTES);
        Object valueAfterRecovery = subject.evalFeature("exp-flag", Object.class).getValue();

        // Then
        assertEquals(storedKey, storedAfterFailedLoad);
        assertEquals(storedValue, valueAfterRecovery);
    }

    @Test
    @DisplayName("Keeps saving disabled when a refresh throws after a failed load")
    void refreshThatThrowsAfterFailedLoadStillSavesNothing() {
        // Given
        Map<String, String> assignments = new HashMap<>();
        assignments.put("exp-flag__0", "1");
        FlakyStickyBucketService service = new FlakyStickyBucketService();
        service.delegate.saveAssignments(new StickyAssignmentsDocument("id", "user-1", assignments));
        service.failLoads = true;
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(EXPERIMENT_FEATURE)
                .attributesJson(ATTRIBUTES)
                .stickyBucketService(service)
                .stickyBucketIdentifierAttributes(Collections.singletonList("id"))
                .build());

        // When
        assertThrows(RuntimeException.class, () -> subject.featuresAPIModelSuccessfully("{not json"));
        subject.evalFeature("exp-flag", Object.class);

        // Then
        assertEquals(assignments, service.delegate.getAssignments("id", "user-1").getAssignments());
    }

    private static final class FailingStickyBucketService implements StickyBucketService {
        private final AtomicInteger loads = new AtomicInteger();
        private final AtomicInteger saves = new AtomicInteger();

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            loads.incrementAndGet();
            throw new IllegalStateException("sticky store unavailable");
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            saves.incrementAndGet();
            throw new IllegalStateException("sticky store unavailable");
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            loads.incrementAndGet();
            throw new IllegalStateException("sticky store unavailable");
        }
    }

    private static final class FlakyStickyBucketService implements StickyBucketService {
        private final InMemoryStickyBucketServiceImpl delegate = new InMemoryStickyBucketServiceImpl();
        private volatile boolean failLoads;

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
                throw new IllegalStateException("sticky store unavailable");
            }
            return delegate.getAllAssignments(attributes);
        }
    }
}
