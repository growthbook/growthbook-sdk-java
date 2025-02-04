package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


class GrowthBookClientTest {
    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();

    // Mock instances that might be needed across tests
    private GBFeaturesRepository mockRepository;
    private GBFeaturesRepository.GBFeaturesRepositoryBuilder mockBuilder;

    @AfterEach
    void tearDown() throws Exception {
        // Reset static repository after each test
        Field field = GrowthBookClient.class.getDeclaredField("repository");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void test_initialization_withValidConfiguration() throws FeatureFetchException {
        mockRepository = createMockRepository("{}", "{}");
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

    //@Test
    void test_evalFeature_withUserContext() {
        String attributes = "{ \"user_group\": \"subscriber\", \"beta_users\": true }";

        mockRepository = createMockRepository("{}", "{}");
        mockBuilder = createMockBuilder(mockRepository);
        FeatureRefreshCallback mockCallback = mock(FeatureRefreshCallback.class);

        UserContext userContext = UserContext.builder()
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

    private GBFeaturesRepository createMockRepository(String features, String savedGroups) {
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
        when(builder.isCacheDisabled(anyBoolean())).thenReturn(builder);
        when(builder.requestBodyForRemoteEval(any())).thenReturn(builder);
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
}