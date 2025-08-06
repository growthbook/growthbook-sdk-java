package growthbook.sdk.java;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EvaluateFeatureWithStickyBucketingFeatureTest {
    private static TestCasesJsonHelper helper;
    private static GrowthBookJsonUtils utils;

    private StickyBucketService stickyBucketService;

    @BeforeAll
    static void beforeAll() {
        helper = TestCasesJsonHelper.getInstance();
        utils = GrowthBookJsonUtils.getInstance();

    }

    @BeforeEach
    void setUp() {
        stickyBucketService = new InMemoryStickyBucketServiceImpl(new HashMap<>());
    }

    @Test
    void testsStickyBucketingFeature() {
        List<String> passedTests = new ArrayList<>();
        List<String> failedTests = new ArrayList<>();
        List<Integer> failingIndexes = new ArrayList<>();
        JsonArray stickyBucketTestCases = helper.getStickyBucketTestCases();
        for (int i = 0; i < stickyBucketTestCases.size(); i++) {
            JsonArray testCase = stickyBucketTestCases.get(i).getAsJsonArray();
            // name of testcase
            String description = testCase.get(0).getAsString();

            // create features json for context
            JsonElement featuresJson = testCase.get(1).getAsJsonObject().get("features");
            String featuresJsonAsStringOrNull = featuresJson == null ? null : featuresJson.toString();

            // create attributes json for context
            JsonElement attributesJson = testCase.get(1).getAsJsonObject().get("attributes");
            String attributesJsonAsStringOrNull = attributesJson == null ? null : attributesJson.toString();

            // create sticky assignments document map for context
            JsonArray stickyAssignmentsJsonArray = testCase.get(2).getAsJsonArray();
            List<StickyAssignmentsDocument> stickyAssigmentList = new ArrayList<>();
            for (JsonElement element : stickyAssignmentsJsonArray) {
                StickyAssignmentsDocument stickyAssignmentsDocument = utils.gson.fromJson(
                        element,
                        StickyAssignmentsDocument.class
                );
                stickyAssigmentList.add(stickyAssignmentsDocument);
            }

            Map<String, StickyAssignmentsDocument> initialStickyBucketAssignmentDocs = new HashMap<>();
            for (StickyAssignmentsDocument doc : stickyAssigmentList) {
                String key = doc.getAttributeName() + "||" + doc.getAttributeValue();
                initialStickyBucketAssignmentDocs.put(key, doc);
            }

            // initialize actual data
            GBContext context = GBContext
                    .builder()
                    .featuresJson(featuresJsonAsStringOrNull)
                    .attributesJson(attributesJsonAsStringOrNull)
                    .stickyBucketService(stickyBucketService)
                    .stickyBucketAssignmentDocs(initialStickyBucketAssignmentDocs)
                    .build();

            GBFeaturesRepository repository = new GBFeaturesRepository(
                    "https://cdn.growthbook.io",
                    "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                    null,
                    null,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null,
                    null
            );

            try {
                if (featuresJson != null) {
                    Type featureMapType = new TypeToken<Map<String, Feature<?>>>() {
                    }.getType();
                    Map<String, Feature<?>> featuresMap = utils.gson.fromJson(featuresJson, featureMapType);

                    Field parsedFeaturesField = GBFeaturesRepository.class.getDeclaredField("parsedFeatures");
                    parsedFeaturesField.setAccessible(true);
                    parsedFeaturesField.set(repository, featuresMap);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            GrowthBook subject = new GrowthBook(context, repository);
            FeatureResult<Object> actualFeatureResult = subject.evalFeature(
                    testCase.get(3).getAsJsonPrimitive().getAsString(),
                    Object.class
            );
            ExperimentResult<Object> actualExperimentResult = actualFeatureResult == null ? null : actualFeatureResult.getExperimentResult();
            Map<String, StickyAssignmentsDocument> actualStickyBucketAssignmentDocs = subject.evaluationContext.getUser().getStickyBucketAssignmentDocs();

            // initialize expected data
            ExperimentResult expectedExperimentResult = (testCase.get(4) instanceof JsonNull) ? null : utils.gson.fromJson(
                    testCase.get(4).getAsJsonObject(),
                    ExperimentResult.class
            );
//            Map<String, StickyAssignmentsDocument> expectedStickyAssignmentsDocument = new HashMap<>();
            Map<String, StickyAssignmentsDocument> expectedStickyAssignmentsDocument = utils.gson.fromJson(
                    testCase.get(5),
                    new TypeToken<HashMap<String, StickyAssignmentsDocument>>() {
                    }.getType()
            );

            String status = "\n" + description + expectedExperimentResult + "&" + expectedStickyAssignmentsDocument + "\n\n"
                    + "\n" + actualExperimentResult + "&" + actualStickyBucketAssignmentDocs;

            if (Objects.equals(actualExperimentResult, expectedExperimentResult) && expectedStickyAssignmentsDocument.equals(actualStickyBucketAssignmentDocs)) {
                passedTests.add(status);
            } else {
                failingIndexes.add(i);
                failedTests.add(status);
            }
            System.out.println(passedTests);
            System.out.println(failedTests);
            System.out.println("FAILED INDEX: " + failingIndexes);

        }
        assertEquals(0, failedTests.size());
    }
}
