package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
            verify(mockRepository).onFeaturesRefresh(mockCallback);
            verify(mockRepository).addFeatureRefreshListener(any());
        }
    }

    @Test
    void test_featureRefreshListener_receivesSuccessEvent() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.addFeatureRefreshListener(listener);

            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());

            FeatureRefreshEvent repositoryEvent = FeatureRefreshEvent.success(
                    true,
                    false,
                    1,
                    FeatureRefreshSource.INITIALIZATION,
                    FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                    12
            );
            repositoryListenerCaptor.getValue().onRefresh(repositoryEvent);

            ArgumentCaptor<FeatureRefreshEvent> eventCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshEvent.class);
            verify(listener).onRefresh(eventCaptor.capture());

            FeatureRefreshEvent event = eventCaptor.getValue();
            assertTrue(event.isSuccessful());
            assertTrue(event.isFeaturesChanged());
            assertFalse(event.isLoadedFromCache());
            assertNull(event.getError());
            assertEquals(1, event.getActiveFeatureCount());
            assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
            assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, event.getRefreshStrategy());
            assertEquals(12, event.getDurationMillis());
            assertNotNull(event.getTimestamp());
        }
    }

    @Test
    void test_featureRefreshListener_receivesFailureEvent() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        RuntimeException error = new RuntimeException("refresh failed");

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.addFeatureRefreshListener(listener);

            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());

            FeatureRefreshEvent repositoryEvent = FeatureRefreshEvent.failure(
                    error,
                    false,
                    true,
                    1,
                    FeatureRefreshSource.SSE,
                    FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                    8
            );
            repositoryListenerCaptor.getValue().onRefresh(repositoryEvent);

            ArgumentCaptor<FeatureRefreshEvent> eventCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshEvent.class);
            verify(listener).onRefresh(eventCaptor.capture());

            FeatureRefreshEvent event = eventCaptor.getValue();
            assertFalse(event.isSuccessful());
            assertFalse(event.isFeaturesChanged());
            assertTrue(event.isLoadedFromCache());
            assertSame(error, event.getError());
            assertEquals(1, event.getActiveFeatureCount());
            assertEquals(FeatureRefreshSource.SSE, event.getSource());
            assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, event.getRefreshStrategy());
            assertEquals(8, event.getDurationMillis());
            assertNotNull(event.getTimestamp());
        }
    }

    @Test
    void test_featureRefreshListener_receivesEventWhenGlobalContextRefreshFails() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.addFeatureRefreshListener(listener);

            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());

            FeatureRefreshEvent repositoryEvent = successEvent();
            when(mockRepository.getParsedFeatures()).thenThrow(new IllegalStateException("feature state unavailable"));
            repositoryListenerCaptor.getValue().onRefresh(repositoryEvent);

            verify(listener).onRefresh(repositoryEvent);
        }
    }

    @Test
    void test_featureRefreshListener_skipsGlobalContextRefreshWhenFeaturesUnchanged() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.addFeatureRefreshListener(listener);

            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());

            // Ignore feature reads performed during initialization; assert only on the unchanged event below.
            clearInvocations(mockRepository);

            FeatureRefreshEvent unchangedEvent = FeatureRefreshEvent.success(
                    false,
                    false,
                    1,
                    FeatureRefreshSource.POLLING,
                    FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                    1
            );
            repositoryListenerCaptor.getValue().onRefresh(unchangedEvent);

            // Listener is always notified, but the global context is not rebuilt when nothing changed.
            verify(listener).onRefresh(unchangedEvent);
            verify(mockRepository, never()).getParsedFeatures();
        }
    }

    @Test
    void test_removeFeatureRefreshListener_stopsNotifications() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.addFeatureRefreshListener(listener);
            client.removeFeatureRefreshListener(listener);

            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());
            repositoryListenerCaptor.getValue().onRefresh(successEvent());

            verify(listener, never()).onRefresh(any());
        }
    }

    @Test
    void test_featureRefreshSubscription_stopsNotificationsWhenClosed() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            FeatureRefreshSubscription subscription = client.subscribeFeatureRefreshListener(listener);
            subscription.close();
            subscription.close();
            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());
            repositoryListenerCaptor.getValue().onRefresh(successEvent());

            verify(listener, never()).onRefresh(any());
        }
    }

    @Test
    void test_featureRefreshListener_usesConfiguredExecutor() {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        Executor executor = mock(Executor.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = Options.builder()
                    .apiHost("https://custom.growthbook.io")
                    .clientKey("custom_key")
                    .decryptionKey("test_key")
                    .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                    .featureRefreshListenerExecutor(executor)
                    .build();
            GrowthBookClient client = new GrowthBookClient(options);
            client.addFeatureRefreshListener(listener);
            assertTrue(client.initialize());

            ArgumentCaptor<FeatureRefreshListener> repositoryListenerCaptor =
                    ArgumentCaptor.forClass(FeatureRefreshListener.class);
            verify(mockRepository).addFeatureRefreshListener(repositoryListenerCaptor.capture());
            FeatureRefreshEvent event = successEvent();
            repositoryListenerCaptor.getValue().onRefresh(event);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(runnableCaptor.capture());
            verify(listener, never()).onRefresh(any());

            runnableCaptor.getValue().run();
            verify(listener).onRefresh(event);
        }
    }

    @Test
    void test_shutdown_doesNotStopCallerSuppliedExecutor() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        ExecutorService suppliedExecutor = mock(ExecutorService.class);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            Options options = Options.builder()
                    .apiHost("https://custom.growthbook.io")
                    .clientKey("custom_key")
                    .decryptionKey("test_key")
                    .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                    .featureRefreshListenerExecutor(suppliedExecutor)
                    .build();
            GrowthBookClient client = new GrowthBookClient(options);
            assertTrue(client.initialize());

            client.shutdown();

            verify(mockRepository).shutdown();
            verify(suppliedExecutor, never()).shutdown();
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
    void test_initializeCanRetryAfterRepositoryInitializationFails() throws FeatureFetchException {
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
    void test_refreshFeature_beforeInitializeDoesNotThrow() {
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));

        assertDoesNotThrow(client::refreshFeature);
    }

    @Test
    void test_refreshForRemoteEval_beforeInitializeDoesNotThrow() {
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));

        assertDoesNotThrow(() -> client.refreshForRemoteEval(null));
    }

    @Test
    void test_clientsUseIndependentRepositories() throws FeatureFetchException {
        GBFeaturesRepository firstRepository = createMockRepository();
        GBFeaturesRepository secondRepository = createMockRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder firstBuilder = createMockBuilder(firstRepository);
        GBFeaturesRepository.GBFeaturesRepositoryBuilder secondBuilder = createMockBuilder(secondRepository);

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(firstBuilder, secondBuilder);

            GrowthBookClient firstClient = new GrowthBookClient(createDefaultOptions(null));
            GrowthBookClient secondClient = new GrowthBookClient(createDefaultOptions(null));

            assertTrue(firstClient.initialize());
            assertTrue(secondClient.initialize());

            verify(firstRepository).initialize();
            verify(secondRepository).initialize();

            firstClient.shutdown();
            verify(firstRepository).shutdown();
            verify(secondRepository, never()).shutdown();
        }
    }

    @Test
    void test_shutdownDuringInitializationClosesRepositoryAfterInitializationExits() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        GrowthBookClient[] clientHolder = new GrowthBookClient[1];

        doAnswer(invocation -> {
            clientHolder[0].shutdown();
            return null;
        }).when(mockRepository).initialize();

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            clientHolder[0] = client;

            assertFalse(client.initialize());

            verify(mockRepository).initialize();
            verify(mockRepository).shutdown();
        }
    }

    @Test
    void test_shutdownBeforeRepositoryInitializationSkipsRepositoryInitialization() throws FeatureFetchException {
        mockRepository = createMockRepository();
        mockBuilder = createMockBuilder(mockRepository);
        GrowthBookClient[] clientHolder = new GrowthBookClient[1];

        when(mockBuilder.build()).thenAnswer(invocation -> {
            clientHolder[0].shutdown();
            return mockRepository;
        });

        try (MockedStatic<GBFeaturesRepository> mockedStatic = mockStatic(GBFeaturesRepository.class)) {
            mockedStatic.when(GBFeaturesRepository::builder).thenReturn(mockBuilder);

            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            clientHolder[0] = client;

            assertFalse(client.initialize());

            verify(mockRepository, never()).onFeaturesRefresh(any());
            verify(mockRepository, never()).addFeatureRefreshListener(any());
            verify(mockRepository, never()).initialize();
            verify(mockRepository).shutdown();
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

    @Test
    void test_endToEnd_realRepositoryRefreshNotifiesListenerAndUpdatesContext() throws Exception {
        HttpServer server = startFeatureServer(
                HttpURLConnection.HTTP_OK,
                "{\"features\":{\"test-feature\":{\"defaultValue\":true}}}"
        );
        GrowthBookClient client = null;
        try {
            Options options = Options.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            client = new GrowthBookClient(options);

            CountDownLatch notified = new CountDownLatch(1);
            AtomicReference<FeatureRefreshEvent> received = new AtomicReference<>();
            client.addFeatureRefreshListener(event -> {
                received.set(event);
                notified.countDown();
            });

            assertTrue(client.initialize());

            // Listener is dispatched off the refresh thread, so wait for the owned executor to deliver it.
            assertTrue(notified.await(5, TimeUnit.SECONDS), "listener was not notified after refresh");
            FeatureRefreshEvent event = received.get();
            assertTrue(event.isSuccessful());
            assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
            assertTrue(event.isFeaturesChanged());

            // Proves the whole chain wired through: repository -> notifier -> bridge -> global context.
            UserContext userContext = UserContext.builder().attributesJson("{\"id\":\"1\"}").build();
            assertTrue(client.isOn("test-feature", userContext));
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

    private GBFeaturesRepository createMockRepository() {
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        HashMap<String, Feature<?>> features = new HashMap<>();
        features.put("test-feature", new Feature<>());
        when(repository.getInitialized()).thenReturn(true);
        when(repository.getFeaturesJson()).thenReturn("{}");
        when(repository.getSavedGroupsJson()).thenReturn("{}");
        when(repository.getParsedFeatures()).thenReturn(features);
        when(repository.getParsedSavedGroups()).thenReturn(new JsonObject());
        when(repository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
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
                // Same-thread dispatch keeps listener-routing assertions deterministic; the real
                // off-thread default executor is exercised by the end-to-end integration test.
                .featureRefreshListenerExecutor(Runnable::run)
                .build();
    }

    private FeatureRefreshEvent successEvent() {
        return FeatureRefreshEvent.success(
                true,
                false,
                1,
                FeatureRefreshSource.MANUAL,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                1
        );
    }
}
