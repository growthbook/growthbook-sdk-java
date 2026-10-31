package growthbook.sdk.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrowthBookStickyBucketingTest {

    private static final GrowthBookJsonUtils JSON = GrowthBookJsonUtils.getInstance();

    @ParameterizedTest(name = "{0}")
    @MethodSource("stickyBucketCases")
    @DisplayName("Evaluates the shared stickyBucket spec cases")
    void evaluatesSharedStickyBucketSpecCases(String description, JsonArray testCase) {
        // Given
        JsonObject input = testCase.get(1).getAsJsonObject();
        JsonElement features = input.get("features");
        JsonElement attributes = input.get("attributes");
        StickyBucketService stickyBucketService = new InMemoryStickyBucketServiceImpl(new HashMap<>());
        for (JsonElement element : testCase.get(2).getAsJsonArray()) {
            stickyBucketService.saveAssignments(JSON.gson.fromJson(element, StickyAssignmentsDocument.class));
        }
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(features == null ? null : features.toString())
                .attributesJson(attributes == null ? null : attributes.toString())
                .stickyBucketService(stickyBucketService)
                .stickyBucketAssignmentDocs(stickyBucketService.getAllAssignments(attributesAsStrings(attributes)))
                .build());

        // When
        FeatureResult<Object> result = subject.evalFeature(testCase.get(3).getAsString(), Object.class);

        // Then
        ExperimentResult<?> expectedExperimentResult = testCase.get(4).isJsonNull()
                ? null
                : JSON.gson.fromJson(testCase.get(4).getAsJsonObject(), ExperimentResult.class);
        Map<String, StickyAssignmentsDocument> expectedDocs = JSON.gson.fromJson(
                testCase.get(5), new TypeToken<HashMap<String, StickyAssignmentsDocument>>() {}.getType());
        assertEquals(expectedExperimentResult, result.getExperimentResult());
        assertEquals(expectedDocs, stickyBucketService.getAllAssignments(attributesAsStrings(attributes)));
    }

    private static Map<String, String> attributesAsStrings(JsonElement attributes) {
        Map<String, String> result = new HashMap<>();
        if (attributes != null && attributes.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : attributes.getAsJsonObject().entrySet()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private static Stream<Arguments> stickyBucketCases() {
        JsonArray cases = TestCasesJsonHelper.getInstance().getStickyBucketTestCases();
        return StreamSupport.stream(cases.spliterator(), false)
                .map(JsonElement::getAsJsonArray)
                .map(testCase -> Arguments.of(testCase.get(0).getAsString(), testCase));
    }
}
