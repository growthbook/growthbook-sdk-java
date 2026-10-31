package growthbook.sdk.java;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("Falls back to normal bucketing when the sticky bucket service throws on load and save")
    void fallsBackToNormalBucketingWhenStickyBucketServiceThrows() {
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
        assertTrue(service.saves.get() > 0);
        assertEquals(expected.getValue(), result.getValue());
        assertEquals(FeatureResultSource.EXPERIMENT, result.getSource());
        assertEquals(expected.getExperimentResult().getVariationId(), result.getExperimentResult().getVariationId());
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
}
