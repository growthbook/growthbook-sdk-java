package growthbook.sdk.java.stickyBucketing;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.InMemoryCachingManagerImpl;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class FileStickyBucketServiceImplTest {
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
        GbCacheManager cacheManager = new InMemoryCachingManagerImpl();
        stickyBucketService = new FileStickyBucketServiceImpl(cacheManager);
    }

    @Test
    void testStickyBucketingFeatureWithFileService() {
        List<String> failedTests = new ArrayList<>();
        JsonArray stickyBucketTestCases = helper.getStickyBucketTestCases();

        for (int i = 0; i < stickyBucketTestCases.size(); i++) {
            JsonArray testCase = stickyBucketTestCases.get(i).getAsJsonArray();
            String description = testCase.get(0).getAsString();

            JsonElement featuresJson = testCase.get(1).getAsJsonObject().get("features");
            String featuresJsonAsStringOrNull = featuresJson == null ? null : featuresJson.toString();

            JsonElement attributesJson = testCase.get(1).getAsJsonObject().get("attributes");
            String attributesJsonAsStringOrNull = attributesJson == null ? null : attributesJson.toString();

            JsonArray stickyAssignmentsJsonArray = testCase.get(2).getAsJsonArray();
            List<StickyAssignmentsDocument> stickyAssigmentList = new ArrayList<>();
            for (JsonElement element : stickyAssignmentsJsonArray) {
                stickyAssigmentList.add(utils.gson.fromJson(element, StickyAssignmentsDocument.class));
            }

            Map<String, StickyAssignmentsDocument> initialDocs = new HashMap<>();
            for (StickyAssignmentsDocument doc : stickyAssigmentList) {
                String key = doc.getAttributeName() + "||" + doc.getAttributeValue();
                initialDocs.put(key, doc);
                stickyBucketService.saveAssignments(doc); // зберігаємо в файловий кеш
            }

            GBContext context = GBContext
                    .builder()
                    .featuresJson(featuresJsonAsStringOrNull)
                    .attributesJson(attributesJsonAsStringOrNull)
                    .stickyBucketService(stickyBucketService)
                    .stickyBucketAssignmentDocs(initialDocs)
                    .build();

            GBFeaturesRepository repository = Mockito.mock(GBFeaturesRepository.class);
            Type featureMapType = new TypeToken<Map<String, Feature<?>>>() {
            }.getType();
            Map<String, Feature<?>> featuresMap = utils.gson.fromJson(featuresJson, featureMapType);
            when(repository.getParsedFeatures()).thenReturn(featuresMap);

            GrowthBook subject = new GrowthBook(context, repository);

            FeatureResult<Object> actualFeatureResult = subject.evalFeature(
                    testCase.get(3).getAsJsonPrimitive().getAsString(),
                    Object.class
            );

            ExperimentResult<Object> actualExperimentResult = actualFeatureResult == null ? null : actualFeatureResult.getExperimentResult();
            Map<String, StickyAssignmentsDocument> actualStickyDocs = subject.evaluationContext.getUser().getStickyBucketAssignmentDocs();

            ExperimentResult expectedExperimentResult = (testCase.get(4) instanceof JsonNull) ? null : utils.gson.fromJson(
                    testCase.get(4).getAsJsonObject(),
                    ExperimentResult.class
            );

            Map<String, StickyAssignmentsDocument> expectedStickyDocs = utils.gson.fromJson(
                    testCase.get(5),
                    new TypeToken<HashMap<String, StickyAssignmentsDocument>>() {
                    }.getType()
            );

            if (!Objects.equals(actualExperimentResult, expectedExperimentResult) ||
                    !Objects.equals(actualStickyDocs, expectedStickyDocs)) {
                failedTests.add("Failed test: " + description);
            }
        }

        assertEquals(0, failedTests.size(), "Some sticky bucket tests failed: " + failedTests);
    }
}
