package growthbook.sdk.java.multiusermode;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.stickyBucketing.SyncOffloadStickyBucketAdapter;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Runs the shared cases.json {@code stickyBucket} corpus against the
 * multi-user {@code GrowthBookClient} as a cross-product of service flavor
 * (offloaded sync / async-native) and API (sync / {@code evalFeatureAsync}),
 * with per-case test names. The legacy single-user runner
 * ({@code EvaluateFeatureWithStickyBucketingFeatureTest}) keeps covering the
 * {@code GrowthBook} class.
 */
class GrowthBookClientStickyBucketConformanceTest {

    private enum ServiceFlavor { SYNC_OFFLOADED, ASYNC_NATIVE }

    private enum Api { SYNC, ASYNC }

    /** Minimal async-native in-memory store, mirroring InMemoryStickyBucketServiceImpl. */
    private static final class AsyncInMemoryStickyService implements AsyncStickyBucketService {
        private final InMemoryStickyBucketServiceImpl delegate = new InMemoryStickyBucketServiceImpl();

        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return java.util.concurrent.CompletableFuture.completedFuture(delegate.getAssignments(attributeName, attributeValue));
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
            delegate.saveAssignments(doc);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        StickyAssignmentsDocument stored(String key) {
            String[] parts = key.split("\\|\\|", 2);
            return delegate.getAssignments(parts[0], parts[1]);
        }
    }

    static Stream<Arguments> stickyBucketCases() {
        JsonArray cases = TestCasesJsonHelper.getInstance().getStickyBucketTestCases();
        List<Arguments> arguments = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            JsonArray testCase = cases.get(i).getAsJsonArray();
            String description = testCase.get(0).getAsString();
            for (ServiceFlavor flavor : ServiceFlavor.values()) {
                for (Api api : Api.values()) {
                    arguments.add(Arguments.of(description + " [" + flavor + "/" + api + "]", i, flavor, api));
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stickyBucketCases")
    void conformance(String name, int caseIndex, ServiceFlavor flavor, Api api) throws Exception {
        GrowthBookJsonUtils utils = GrowthBookJsonUtils.getInstance();
        JsonArray testCase = TestCasesJsonHelper.getInstance()
                .getStickyBucketTestCases().get(caseIndex).getAsJsonArray();

        JsonElement featuresJson = testCase.get(1).getAsJsonObject().get("features");
        JsonElement attributesJson = testCase.get(1).getAsJsonObject().get("attributes");
        String featureKey = testCase.get(3).getAsJsonPrimitive().getAsString();

        // Seed the store with the case's input documents.
        InMemoryStickyBucketServiceImpl syncService = new InMemoryStickyBucketServiceImpl();
        AsyncInMemoryStickyService asyncService = new AsyncInMemoryStickyService();
        for (JsonElement element : testCase.get(2).getAsJsonArray()) {
            StickyAssignmentsDocument doc = utils.gson.fromJson(element, StickyAssignmentsDocument.class);
            syncService.saveAssignments(doc);
            asyncService.saveAssignments(doc);
        }

        Options.OptionsBuilder optionsBuilder = Options.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("conformance-key");
        if (flavor == ServiceFlavor.SYNC_OFFLOADED) {
            optionsBuilder.stickyBucketService(syncService);
        } else {
            optionsBuilder.asyncStickyBucketService(asyncService);
        }

        Map<String, Feature<?>> parsedFeatures = featuresJson == null
                ? new HashMap<>()
                : TransformationUtil.transformFeatures(featuresJson.toString());

        GBFeaturesRepository repository = mockRepository(parsedFeatures);
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = mockBuilder(repository);
        GrowthBookClient client;
        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(builder);
            client = new GrowthBookClient(optionsBuilder.build());
            client.initialize();
        }

        try {
            UserContext user = UserContext.builder()
                    .attributesJson(attributesJson == null ? "{}" : attributesJson.toString())
                    .build();

            FeatureResult<Object> actualFeatureResult;
            if (api == Api.SYNC) {
                actualFeatureResult = client.evalFeature(featureKey, Object.class, user);
            } else {
                actualFeatureResult = client.evalFeatureAsync(featureKey, Object.class, user)
                        .get(10, TimeUnit.SECONDS);
            }
            client.flushStickyBucketSaves().get(10, TimeUnit.SECONDS);

            ExperimentResult<Object> actualExperimentResult =
                    actualFeatureResult == null ? null : actualFeatureResult.getExperimentResult();
            ExperimentResult<Object> expectedExperimentResult = (testCase.get(4) instanceof JsonNull)
                    ? null
                    : utils.gson.fromJson(testCase.get(4).getAsJsonObject(), ExperimentResult.class);
            assertEquals(expectedExperimentResult, actualExperimentResult,
                    "experiment result mismatch for " + name);

            // The persisted store must contain, for every expected document key,
            // exactly the expected assignments (the case's expected map is the
            // post-eval merged state for the addressed keys).
            Map<String, StickyAssignmentsDocument> expectedDocs = utils.gson.fromJson(
                    testCase.get(5), new TypeToken<HashMap<String, StickyAssignmentsDocument>>() { }.getType());
            for (Map.Entry<String, StickyAssignmentsDocument> expected : expectedDocs.entrySet()) {
                StickyAssignmentsDocument stored = flavor == ServiceFlavor.SYNC_OFFLOADED
                        ? syncService.getAssignments(expected.getValue().getAttributeName(),
                        expected.getValue().getAttributeValue())
                        : asyncService.stored(expected.getKey());
                assertNotNull(stored, "expected persisted doc missing: " + expected.getKey());
                assertEquals(expected.getValue().getAssignments(), stored.getAssignments(),
                        "persisted assignments mismatch for " + expected.getKey());
            }
        } finally {
            client.shutdown();
        }
    }

    private static GBFeaturesRepository mockRepository(Map<String, Feature<?>> parsedFeatures) {
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getInitialized()).thenReturn(true);
        when(repository.getParsedFeatures()).thenReturn(parsedFeatures);
        when(repository.getParsedSavedGroups()).thenReturn(new JsonObject());
        when(repository.getFeatureSnapshot()).thenReturn(
                FeatureSnapshot.of("{}", "{}", parsedFeatures, new JsonObject()));
        return repository;
    }

    private static GBFeaturesRepository.GBFeaturesRepositoryBuilder mockBuilder(GBFeaturesRepository repository) {
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                mock(GBFeaturesRepository.GBFeaturesRepositoryBuilder.class);
        when(builder.apiHost(anyString())).thenReturn(builder);
        when(builder.clientKey(anyString())).thenReturn(builder);
        when(builder.decryptionKey(any())).thenReturn(builder);
        when(builder.refreshStrategy(any())).thenReturn(builder);
        when(builder.swrTtlSeconds(any())).thenReturn(builder);
        when(builder.isCacheDisabled(anyBoolean())).thenReturn(builder);
        when(builder.requestBodyForRemoteEval(any())).thenReturn(builder);
        when(builder.cacheManager(any())).thenReturn(builder);
        when(builder.backgroundFetchInterval(any())).thenReturn(builder);
        when(builder.retryPolicy(any())).thenReturn(builder);
        when(builder.build()).thenReturn(repository);
        return builder;
    }
}
