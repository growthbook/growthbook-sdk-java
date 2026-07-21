package growthbook.sdk.java.multiusermode;

import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.model.TypedKey;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createDefaultOptions;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockBuilder;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrowthBookClientTest {

    // Mock instances that might be needed across tests
    private GBFeaturesRepository mockRepository;
    private GBFeaturesRepository.GBFeaturesRepositoryBuilder mockBuilder;

    @Test
    void initialize_validConfiguration_registersCallbacksAndReturnsTrue() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            verify(mockRepository).initialize();

            // Capture the callbacks for inspection
            ArgumentCaptor<FeatureRefreshCallback> callbackCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshCallback.class);
            verify(mockRepository, times(2)).onFeaturesRefresh(callbackCaptor.capture());

            // Get all captured callbacks
            List<FeatureRefreshCallback> capturedCallbacks = callbackCaptor.getAllValues();
            assertEquals(2, capturedCallbacks.size());

            // One of them should be our mockCallback
            assertTrue(capturedCallbacks.contains(mockCallback),
                    "User callback should be registered");

            // One of them should be the internal callback (instance of anonymous FeatureRefreshCallback)
            assertTrue(capturedCallbacks.stream()
                            .anyMatch(callback -> callback != mockCallback && callback != null),
                    "Internal callback should be registered");
        }
    }

    @Test
    void initialize_repositoryThrows_returnsFalse() throws FeatureFetchException {
        // Configures a mock repository that simulates initialization failure
        mockRepository = mock(GBFeaturesRepository.class);
        when(mockRepository.getInitialized()).thenReturn(false);
        doThrow(new FeatureFetchException(FeatureFetchException
                .FeatureFetchErrorCode.NO_RESPONSE_ERROR)).when(mockRepository).initialize();

        // create the mock builder instance
        mockBuilder = createMockBuilder(mockRepository);

        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            // Initialize client
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mockCallback));

            // 5. Test initialization fails
            boolean result = client.initialize();
            assertFalse(result);

            // 6. Verify initialize was called and threw exception
            verify(mockRepository).initialize();
        }
    }

    @Test
    void shutdown_notInitialized_doesNotCallRepositoryShutdown() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);

            client.shutdown();

            verify(mockRepository, never()).shutdown();
        }
    }

    @Test
    void shutdown_initialized_callsRepositoryShutdown() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            client.initialize();

            client.shutdown();

            verify(mockRepository).shutdown();
        }
    }

    @SneakyThrows
    @Test
    void refreshFeature_afterInitialization_callsDefaultRefresh() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            client.initialize();

            client.refreshFeature();

            verify(mockRepository).refreshFeatures(RefreshMode.DEFAULT);
        }
    }

    @SneakyThrows
    @Test
    void refreshFeature_refreshThrows_doesNotThrow() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            client.initialize();

            doThrow(
                    new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR))
                    .when(mockRepository).refreshFeatures(RefreshMode.DEFAULT);
            client.refreshFeature();

            assertDoesNotThrow(client::refreshFeature);
        }
    }

    @SneakyThrows
    @Test
    void refreshFeatures_forceRefreshBlocksOnRepositoryRefresh() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mock(FeatureRefreshCallback.class)));
            client.initialize();

            client.refreshFeatures(RefreshMode.FORCE);

            verify(mockRepository).refreshFeatures(RefreshMode.FORCE);
            verify(mockRepository, never()).requestFeatureRefresh(RefreshMode.FORCE);
        }
    }

    @SneakyThrows
    @Test
    void refreshForRemoteEval_afterInitialization_callsFetchForRemoteEval() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            client.initialize();

            RequestBodyForRemoteEval mock = mock(RequestBodyForRemoteEval.class);
            client.refreshForRemoteEval(mock);

            verify(mockRepository).fetchForRemoteEval(mock);
        }
    }

    @SneakyThrows
    @Test
    void refreshForRemoteEval_fetchThrows_doesNotThrow() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            client.initialize();

            RequestBodyForRemoteEval mock = mock(RequestBodyForRemoteEval.class);
            doThrow(
                    new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR))
                    .when(mockRepository).fetchForRemoteEval(mock);
            client.refreshForRemoteEval(mock);

            assertDoesNotThrow(() -> client.refreshForRemoteEval(mock));
        }
    }

    @Test
    void setGlobalAttributes_validJson_storesAttributesJson() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        client.setGlobalAttributes("{\"id\":\"user-1\"}");

        assertEquals("{\"id\":\"user-1\"}", options.getAttributesJson());
    }

    @Test
    void setGlobalAttributes_validJson_parsesJsonObject() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        client.setGlobalAttributes("{\"id\":\"user-1\",\"plan\":\"pro\"}");

        assertNotNull(options.getGlobalAttributes());
        assertEquals("user-1", options.getGlobalAttributes().get("id").getAsString());
        assertEquals("pro", options.getGlobalAttributes().get("plan").getAsString());
    }

    @Test
    void setGlobalAttributes_null_setsEmptyJsonObject() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        client.setGlobalAttributes(null);

        assertNull(options.getAttributesJson());
        assertNotNull(options.getGlobalAttributes());
        assertEquals(0, options.getGlobalAttributes().size());
    }

    @Test
    void setGlobalAttributes_calledTwice_overwritesPreviousValue() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        client.setGlobalAttributes("{\"id\":\"first\"}");
        client.setGlobalAttributes("{\"id\":\"second\"}");

        assertEquals("{\"id\":\"second\"}", options.getAttributesJson());
        assertEquals("second", options.getGlobalAttributes().get("id").getAsString());
    }

    @Test
    void setGlobalForceFeatures_validMap_storesMapOnOptions() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        Map<String, Object> forcedFeatures = new HashMap<>();
        forcedFeatures.put("dark-mode", true);
        forcedFeatures.put("max-items", 10);

        client.setGlobalForceFeatures(forcedFeatures);

        assertEquals(forcedFeatures, options.getGlobalForcedFeatureValues());
    }

    @Test
    void setGlobalForceFeatures_null_clearsMap() {
        Options options = Options.builder()
                .globalForcedFeatureValues(new HashMap<>())
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        client.setGlobalForceFeatures(null);

        assertNull(options.getGlobalForcedFeatureValues());
    }

    @Test
    void setGlobalForceFeatures_calledTwice_overwritesPreviousMap() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        Map<String, Object> first = new HashMap<>();
        first.put("feature-a", true);
        client.setGlobalForceFeatures(first);

        Map<String, Object> second = new HashMap<>();
        second.put("feature-b", false);
        client.setGlobalForceFeatures(second);

        assertFalse(options.getGlobalForcedFeatureValues().containsKey("feature-a"));
        assertTrue(options.getGlobalForcedFeatureValues().containsKey("feature-b"));
    }

    @Test
    void setGlobalForceVariations_validMap_storesMapOnOptions() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        Map<String, Integer> forcedVariations = new HashMap<>();
        forcedVariations.put("experiment-1", 1);
        forcedVariations.put("experiment-2", 0);

        client.setGlobalForceVariations(forcedVariations);

        assertEquals(forcedVariations, options.getGlobalForcedVariationsMap());
    }

    @Test
    void setGlobalForceVariations_null_clearsMap() {
        Options options = Options.builder()
                .globalForcedVariationsMap(new HashMap<>())
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        client.setGlobalForceVariations(null);

        assertNotNull(options.getGlobalForcedVariationsMap());
        assertTrue(options.getGlobalForcedVariationsMap().isEmpty());
    }

    @Test
    void setGlobalForceVariations_calledTwice_overwritesPreviousMap() {
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        Map<String, Integer> first = new HashMap<>();
        first.put("exp-a", 1);
        client.setGlobalForceVariations(first);

        Map<String, Integer> second = new HashMap<>();
        second.put("exp-b", 0);
        client.setGlobalForceVariations(second);

        assertFalse(options.getGlobalForcedVariationsMap().containsKey("exp-a"));
        assertTrue(options.getGlobalForcedVariationsMap().containsKey("exp-b"));
    }

    @SneakyThrows
    @Test
    void refreshGlobalContext_globalContextNull_recreatesGlobalContext() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);
        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mockCallback));
            client.initialize();


            ArgumentCaptor<FeatureRefreshCallback> captor =
                    ArgumentCaptor.forClass(FeatureRefreshCallback.class);
            verify(mockRepository, times(2)).onFeaturesRefresh(captor.capture());

            FeatureRefreshCallback internalCallback = captor.getAllValues().stream()
                    .filter(cb -> cb != mockCallback)
                    .findFirst().orElseThrow(RuntimeException::new);

            Field field = GrowthBookClient.class.getDeclaredField("globalContext");
            field.setAccessible(true);
            AtomicReference<GlobalContext> reference = (AtomicReference<GlobalContext>) field.get(client);
            reference.set(null);

            internalCallback.onRefresh("{}");

            GlobalContext ctx = reference.get();
            assertNotNull(ctx);
        }
    }

    @SneakyThrows
    @Test
    void refreshGlobalContext_repositoryUpdated_updatesGlobalContextFeatures() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        Map<String, Feature<?>> newFeatures = new HashMap<>();
        when(mockRepository.getParsedFeatures()).thenReturn(newFeatures);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mockCallback));
            client.initialize();

            ArgumentCaptor<FeatureRefreshCallback> captor =
                    ArgumentCaptor.forClass(FeatureRefreshCallback.class);
            verify(mockRepository, times(2)).onFeaturesRefresh(captor.capture());

            FeatureRefreshCallback internalCallback = captor.getAllValues().stream()
                    .filter(cb -> cb != mockCallback)
                    .findFirst().orElseThrow(RuntimeException::new);

            internalCallback.onRefresh("{\"new-feature\":{\"defaultValue\":true}}");

            Field field = GrowthBookClient.class.getDeclaredField("globalContext");
            field.setAccessible(true);
            AtomicReference<GlobalContext> reference = (AtomicReference<GlobalContext>) field.get(client);
            GlobalContext ctx = reference.get();

            assertNotNull(ctx);
            assertSame(newFeatures, ctx.getFeatures());
        }
    }

    @Test
    void getFeatureValue_floatFeature_returnsCorrectValue() {
        String featureKey = "price";
        String attributes = "{ \"user\": \"standard\" }";
        String demoFeaturesJson = TestCasesJsonHelper.getInstance().getDemoFeaturesJson();

        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        Map<String, Feature<?>> parsedFeatures = TransformationUtil.transformFeatures(demoFeaturesJson);
        when(mockRepository.getParsedFeatures()).thenReturn(parsedFeatures);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();

            UserContext userContext = UserContext.builder().attributesJson(attributes).build();

            Float result = client.getFeatureValue(featureKey, 0.0f, Float.class, userContext);

            assertEquals(10.99f, result);
        }
    }

    @Test
    void run_sameResultMultipleTimes_firesCallbackOnce() {
        GrowthBookClient subject = new GrowthBookClient();
        ExperimentRunCallback mockCallback = mock(ExperimentRunCallback.class);
        Experiment<String> mockExperiment = Experiment.<String>builder().build();

        subject.subscribe(mockCallback);
        subject.run(mockExperiment, UserContext.builder().build());
        subject.run(mockExperiment, UserContext.builder().build());
        subject.run(mockExperiment, UserContext.builder().build());

        verify(mockCallback, times(1)).onRun(any(), any());
    }

    @Test
    void isOn_enabledFeature_returnsTrueAndIsOffReturnsFalse() {
        String featureKey = "price";
        String attributes = "{ \"user\": \"standard\" }";
        String demoFeaturesJson = TestCasesJsonHelper.getInstance().getDemoFeaturesJson();

        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        Map<String, Feature<?>> parsedFeatures = TransformationUtil.transformFeatures(demoFeaturesJson);
        when(mockRepository.getParsedFeatures()).thenReturn(parsedFeatures);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();

            UserContext userContext = UserContext.builder().attributesJson(attributes).build();

            FeatureResult<Float> floatFeatureResult = client.evalFeature(featureKey, Float.class, userContext);

            Boolean on = client.isOn(featureKey, userContext);
            Boolean off = client.isOff(featureKey, userContext);

            assertTrue(on);
            assertFalse(off);
            assertTrue(floatFeatureResult.isOn());
        }
    }

    @Test
    void test_typedFeatureAccess_evaluatesWithTypedKeys() throws Exception {
        HttpServer server = startFeatureServer(
                HttpURLConnection.HTTP_OK,
                "{\"features\":{"
                        + "\"new-home\":{\"defaultValue\":true},"
                        + "\"theme\":{\"defaultValue\":\"dark\"},"
                        + "\"max-items\":{\"defaultValue\":25},"
                        + "\"ratio\":{\"defaultValue\":1.5},"
                        // whole-number JSON: exercises the numeric coercion path for float/double
                        + "\"weight\":{\"defaultValue\":2}"
                        + "}}"
        );
        GrowthBookClient client = null;
        try {
            Options options = Options.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            UserContext userContext = UserContext.builder().attributesJson("{\"id\":\"1\"}").build();

            FeatureKey<Boolean> newHome = TypedKey.ofBoolean("new-home");
            FeatureKey<String> theme = TypedKey.ofString("theme");
            FeatureKey<Integer> maxItems = TypedKey.ofInteger("max-items");
            FeatureKey<Double> ratio = TypedKey.ofDouble("ratio");
            FeatureKey<Float> weight = TypedKey.ofFloat("weight");

            FeatureResult<Boolean> result = client.getFeature(newHome, userContext);
            assertNotNull(result);
            assertTrue(result.isOn());

            assertTrue(client.isOn(newHome, userContext));
            assertFalse(client.isOff(newHome, userContext));
            assertTrue(client.getBooleanFeature(newHome, userContext));
            assertEquals("dark", client.getStringFeature(theme, "light", userContext));
            assertEquals(Integer.valueOf(25), client.getIntegerFeature(maxItems, 10, userContext));
            assertEquals(Integer.valueOf(25), client.getFeatureValue(maxItems, 0, userContext));
            assertEquals(Double.valueOf(1.5), client.getDoubleFeature(ratio, 0.0, userContext));
            assertEquals(Float.valueOf(2.0f), client.getFloatFeature(weight, 0.0f, userContext));

            // Unknown key falls back to the supplied default.
            assertFalse(client.getBooleanFeature(TypedKey.ofBoolean("missing"), userContext));
            assertEquals("light", client.getStringFeature(TypedKey.ofString("missing"), "light", userContext));
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    private static final String TEST_CLIENT_KEY = "sdk-test";

    private static HttpServer startFeatureServer(int statusCode, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/" + TEST_CLIENT_KEY, exchange -> {
            byte[] responseBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.X_SSE_SUPPORT.getHeader(), "enabled");
            if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                exchange.sendResponseHeaders(statusCode, -1);
            } else {
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(responseBytes);
                }
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String apiHost(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
