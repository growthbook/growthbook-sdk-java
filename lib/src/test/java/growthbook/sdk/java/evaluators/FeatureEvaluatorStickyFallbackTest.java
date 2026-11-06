package growthbook.sdk.java.evaluators;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureEvaluatorStickyFallbackTest {

    private static final String FEATURES = "{\"rollout\": {\"defaultValue\": false, \"rules\": ["
            + "{\"force\": true, \"coverage\": 1.0, \"hashAttribute\": \"id\", \"fallbackAttribute\": \"deviceId\"}]}}";

    private final FeatureEvaluator featureEvaluator = new FeatureEvaluator();

    @Test
    @DisplayName("Uses the fallback attribute for a rollout when only an async sticky bucket service is configured")
    void usesFallbackAttributeWithAsyncOnlyStickyService() {
        // Given
        Options options = Options.builder().asyncStickyBucketService(new EmptyAsyncStickyService()).build();
        EvaluationContext context = context(options, "{\"deviceId\": \"d-1\"}");

        // When
        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("rollout", context, Boolean.class);

        // Then
        assertEquals(Boolean.TRUE, result.getValue());
    }

    @Test
    @DisplayName("Skips the rollout for a user without the hash attribute when sticky bucketing is off")
    void skipsRolloutWithoutStickyBucketing() {
        // Given
        EvaluationContext context = context(Options.builder().build(), "{\"deviceId\": \"d-1\"}");

        // When
        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("rollout", context, Boolean.class);

        // Then
        assertEquals(Boolean.FALSE, result.getValue());
    }

    private static EvaluationContext context(Options options, String attributesJson) {
        GlobalContext global = GlobalContext.builder().features(TransformationUtil.transformFeatures(FEATURES)).build();
        UserContext user = UserContext.builder().attributesJson(attributesJson).build();
        user.setStickyBucketAssignmentDocs(Collections.<String, StickyAssignmentsDocument>emptyMap());
        return new EvaluationContext(global, user, new EvaluationContext.StackContext(), options);
    }

    private static final class EmptyAsyncStickyService implements AsyncStickyBucketService {
        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
            return CompletableFuture.completedFuture(Collections.<String, StickyAssignmentsDocument>emptyMap());
        }
    }
}
