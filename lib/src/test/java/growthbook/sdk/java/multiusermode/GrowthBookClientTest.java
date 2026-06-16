package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


class GrowthBookClientTest {
    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();

    // Mock instances that might be needed across tests
    private GBFeaturesRepository mockRepository;
    private GBFeaturesRepository.GBFeaturesRepositoryBuilder mockBuilder;

    @Test
    void test_initialization_withValidConfiguration() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);


        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mockCallback);

            GrowthBookClient client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            verify(mockRepository).initialize();
            verify(mockRepository, times(2)).onFeaturesRefresh(any());

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
    void test_multipleClients_keepIndependentRepositories() throws FeatureFetchException {
        GBFeaturesRepository firstRepository = createMockRepository();
        GBFeaturesRepository secondRepository = createMockRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder firstBuilder = createMockBuilder(firstRepository);
        GBFeaturesRepository.GBFeaturesRepositoryBuilder secondBuilder = createMockBuilder(secondRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(firstBuilder, secondBuilder);

            GrowthBookClient firstClient = new GrowthBookClient(createDefaultOptions(mock(FeatureRefreshCallback.class)));
            GrowthBookClient secondClient = new GrowthBookClient(createDefaultOptions(mock(FeatureRefreshCallback.class)));

            assertTrue(firstClient.initialize());
            assertTrue(secondClient.initialize());

            firstClient.shutdown();
            secondClient.refreshFeature();

            verify(firstRepository).shutdown();
            verify(firstRepository, never()).requestFeatureRefresh(RefreshMode.DEFAULT);
            verify(secondRepository, never()).shutdown();
            verify(secondRepository).requestFeatureRefresh(RefreshMode.DEFAULT);

            secondClient.shutdown();
        }
    }

    @Test
    void test_initialization_withFailedFeatureFetch() throws FeatureFetchException {
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

            /*// 6. Verify initialize was called and threw exception
            verify(mockRepository).initialize();

            // 7. Verify no callbacks were registered due to failure
            verify(mockRepository, never()).onFeaturesRefresh(any());*/
        }
    }

    @Test
    void test_initialize_canRetryAfterRepositoryInitializationFails() throws FeatureFetchException {
        GBFeaturesRepository failedRepository = mock(GBFeaturesRepository.class);
        when(failedRepository.getInitialized()).thenReturn(false);
        doThrow(new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR))
                .when(failedRepository).initialize();

        GBFeaturesRepository successfulRepository = createMockRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder failedBuilder = createMockBuilder(failedRepository);
        GBFeaturesRepository.GBFeaturesRepositoryBuilder successfulBuilder = createMockBuilder(successfulRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(failedBuilder, successfulBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mock(FeatureRefreshCallback.class)));

            assertFalse(client.initialize());
            assertTrue(client.initialize());

            verify(failedRepository).initialize();
            verify(failedRepository).shutdown();
            verify(successfulRepository).initialize();
        }
    }

  @Test
  void test_shutdown_withANonInitializedClient() {
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
    void test_shutdown_withAnInitializedClient() {
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

    @Test
    void test_shutdownDoesNotWaitForRepositoryInitializeNetworkCall() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch initializeStarted = new CountDownLatch(1);
        CountDownLatch releaseInitialize = new CountDownLatch(1);
        server.createContext("/api/features/sdk-blocking-init", exchange ->
                handleBlockingFeatureResponse(exchange, initializeStarted, releaseInitialize));
        server.start();

        ExecutorService initializeExecutor = Executors.newSingleThreadExecutor();
        try {
            String apiHost = "http://127.0.0.1:" + server.getAddress().getPort();
            Options options = Options.builder()
                    .apiHost(apiHost)
                    .clientKey("sdk-blocking-init")
                    .retryPolicy(new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO))
                    .build();

            GrowthBookClient client = new GrowthBookClient(options);
            Future<Boolean> initializeResult = initializeExecutor.submit(client::initialize);

            assertTrue(initializeStarted.await(1, TimeUnit.SECONDS), "Repository initialization should start");

            CountDownLatch shutdownReturned = new CountDownLatch(1);
            Thread shutdownThread = new Thread(() -> {
                client.shutdown();
                shutdownReturned.countDown();
            });
            shutdownThread.start();

            assertTrue(shutdownReturned.await(500, TimeUnit.MILLISECONDS),
                    "Shutdown should not wait for repository initialization network call");

            releaseInitialize.countDown();
            assertFalse(initializeResult.get(1, TimeUnit.SECONDS),
                    "Initialization should not report ready after the repository was shut down");
        } finally {
            releaseInitialize.countDown();
            initializeExecutor.shutdownNow();
            server.stop(0);
        }
    }

    @Test
    void test_forceRefresh_delegatesToRepository() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mock(FeatureRefreshCallback.class)));
            client.initialize();
            client.refreshFeatures(RefreshMode.FORCE);

            verify(mockRepository).requestFeatureRefresh(RefreshMode.FORCE);
            verify(mockRepository, never()).refreshFeatures(RefreshMode.FORCE);
            client.shutdown();
        }
    }

    @Test
    void test_legacyRefreshFeature_usesDefaultRefreshFlow() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mock(FeatureRefreshCallback.class)));
            client.initialize();
            client.refreshFeature();

            verify(mockRepository).requestFeatureRefresh(RefreshMode.DEFAULT);
        }
    }

    @Test
    void test_legacyOptionsConstructorRemainsAvailable() {
        Options options = new Options(
                true,
                false,
                false,
                false,
                null,
                "https://custom.growthbook.io",
                "custom_key",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("custom_key", options.getClientKey());
        assertNull(options.getBackgroundFetchInterval());
        assertNull(options.getRetryPolicy());
    }

    @Test
    void test_initialization_forwardsRefreshConfiguration() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        Duration backgroundFetchInterval = Duration.ofHours(48);
        FeatureFetchRetryPolicy retryPolicy = new FeatureFetchRetryPolicy(
                3,
                Duration.ZERO,
                Duration.ZERO
        );

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = createDefaultOptions(mock(FeatureRefreshCallback.class));
            options.setBackgroundFetchInterval(backgroundFetchInterval);
            options.setRetryPolicy(retryPolicy);
            new GrowthBookClient(options).initialize();

            verify(mockBuilder).backgroundFetchInterval(backgroundFetchInterval);
            verify(mockBuilder).retryPolicy(retryPolicy);
        }
    }

    //@Test
    void test_evalFeature_withUserContext() {
        String attributes = "{ \"user_group\": \"subscriber\", \"beta_users\": true }";

        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        UserContext userContext = new UserContext.UserContextBuilder()
                .attributes(jsonUtils.gson.fromJson(attributes, JsonObject.class))
                .build();

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            // Initialize client with appropriate features.
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(mockCallback));

            FeatureResult<Boolean> result = client.evalFeature("new-feature", Boolean.class, userContext);

            assertNotNull(result);
            assertTrue(result.isOn());
            assertFalse(result.isOff());
        }
    }

    private GBFeaturesRepository createMockRepository() {
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getInitialized()).thenReturn(true);
        when(repository.getFeaturesJson()).thenReturn("{}");
        when(repository.getSavedGroupsJson()).thenReturn("{}");
        return repository;
    }

    private GBFeaturesRepository.GBFeaturesRepositoryBuilder createMockBuilder(GBFeaturesRepository repository) {
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                mock(GBFeaturesRepository.GBFeaturesRepositoryBuilder.class);

        when(builder.apiHost(anyString())).thenReturn(builder);
        when(builder.clientKey(anyString())).thenReturn(builder);
        when(builder.decryptionKey(anyString())).thenReturn(builder);
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

    private Options createDefaultOptions(FeatureRefreshCallback callback) {
        return Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .decryptionKey("test_key")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .featureRefreshCallback(callback)
                .build();
    }

    private static void handleBlockingFeatureResponse(
            HttpExchange exchange,
            CountDownLatch initializeStarted,
            CountDownLatch releaseInitialize
    ) throws IOException {
        initializeStarted.countDown();
        try {
            releaseInitialize.await(2, TimeUnit.SECONDS);
            byte[] response = "{\"features\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }
}
