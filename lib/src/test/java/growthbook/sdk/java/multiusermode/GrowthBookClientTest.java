package growthbook.sdk.java.multiusermode;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockBuilder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.FeatureRefreshSubscription;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.TypedKey;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.repository.RefreshMode;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class GrowthBookClientTest {

    private static final String CLIENT_KEY = "sdk-test";
    private static final GrowthBookJsonUtils JSON = GrowthBookJsonUtils.getInstance();

    private final List<GrowthBookClient> clients = new ArrayList<>();
    private MockedStatic<GBFeaturesRepository> repositoryStatics;
    private WireMockServer wireMock;

    @AfterEach
    void tearDown() {
        for (GrowthBookClient client : clients) {
            client.shutdown();
        }
        if (repositoryStatics != null) {
            repositoryStatics.close();
        }
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    @DisplayName("Initializing delegates to the repository and wires the refresh callback and listener bridge")
    @SuppressWarnings("deprecation")
    void initializingWiresRepository() throws FeatureFetchException {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        GrowthBookClient client = new GrowthBookClient(optionsBuilder().featureRefreshCallback(callback).build());

        // When
        boolean initialized = client.initialize();

        // Then
        assertTrue(initialized);
        verify(repository).initialize(true);
        verify(repository).onFeaturesRefresh(callback);
        verify(repository).addFeatureRefreshListener(any());
    }

    @Test
    @DisplayName("Disabling SSE reconnect passes the reconnect flag as false to the repository")
    void disablingSseReconnectPassesFalseToRepository() throws FeatureFetchException {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        Options options = optionsBuilder().sseReconnectOnFailure(false).build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        boolean initialized = client.initialize();

        // Then
        assertTrue(initialized);
        verify(repository).initialize(false);
        verify(repository, never()).initialize(true);
    }

    @Test
    @DisplayName("Before initialization evaluation applies globally forced values over an empty feature set")
    void evaluationBeforeInitializationUsesEmptyFeatureSet() {
        // Given
        GrowthBookClient client = new GrowthBookClient(forcedFeatureOptions());
        UserContext user = user("{\"id\":\"user-1\"}");

        // When
        Boolean on = client.isOn("forced-feature", user);
        FeatureResult<Object> forced = client.evalFeature("forced-feature", Object.class, user);
        FeatureResult<Object> notForced = client.evalFeature("not-forced", Object.class, user);

        // Then
        assertTrue(on);
        assertEquals(FeatureResultSource.OVERRIDE, forced.getSource());
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, notForced.getSource());
    }

    @Test
    @DisplayName("Running an experiment with a condition before initialization does not throw")
    void runningConditionalExperimentBeforeInitializationDoesNotThrow() {
        // Given
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        Experiment<String> experiment = Experiment.<String>builder()
                .key("exp-1")
                .variations(new ArrayList<>(Arrays.asList("a", "b")))
                .conditionJson(JSON.gson.fromJson("{\"id\":\"user-1\"}", JsonObject.class))
                .build();

        // When / Then
        assertDoesNotThrow(() -> client.run(experiment, user("{\"id\":\"user-1\"}")));
    }

    @Test
    @DisplayName("After a failed initialization evaluation still applies globally forced values")
    void evaluationAfterFailedInitializationUsesForcedValues() {
        // Given
        serve(mock(GBFeaturesRepository.class));
        GrowthBookClient client = new GrowthBookClient(forcedFeatureOptions());
        boolean initialized = client.initialize();

        // When
        Boolean on = client.isOn("forced-feature", user("{\"id\":\"user-1\"}"));

        // Then
        assertFalse(initialized);
        assertTrue(on);
    }

    @Test
    @DisplayName("A forced refresh blocks on the repository refresh instead of requesting one in the background")
    void forcedRefreshBlocksOnRepositoryRefresh() throws Exception {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.initialize();

        // When
        client.refreshFeatures(RefreshMode.FORCE);

        // Then
        verify(repository).refreshFeatures(RefreshMode.FORCE);
        verify(repository, never()).requestFeatureRefresh(RefreshMode.FORCE);
    }

    @Test
    @DisplayName("A default refresh also blocks on the repository refresh")
    void defaultRefreshBlocksOnRepositoryRefresh() throws Exception {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.initialize();

        // When
        client.refreshFeatures();

        // Then
        verify(repository).refreshFeatures(RefreshMode.DEFAULT);
        verify(repository, never()).requestFeatureRefresh(any());
    }

    @Test
    @DisplayName("A failing repository refresh does not propagate to the caller")
    void failingRepositoryRefreshDoesNotPropagate() throws Exception {
        // Given
        GBFeaturesRepository repository = stubRepository();
        doThrow(new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR))
                .when(repository).refreshFeatures(RefreshMode.FORCE);
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.initialize();

        // When / Then
        assertDoesNotThrow(() -> client.refreshFeatures(RefreshMode.FORCE));
    }

    @Test
    @DisplayName("A refresh listener receives the repository's success event unchanged")
    void refreshListenerReceivesSuccessEvent() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.addFeatureRefreshListener(listener);
        client.initialize();
        FeatureRefreshEvent repositoryEvent = FeatureRefreshEvent.success(
                true, false, 1, FeatureRefreshSource.INITIALIZATION, FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, 12);

        // When
        repositoryListener(repository).onRefresh(repositoryEvent);

        // Then
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertTrue(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertNull(event.getError());
        assertEquals(1, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, event.getRefreshStrategy());
        assertEquals(12, event.getDurationMillis());
        assertEquals(repositoryEvent.getTimestamp(), event.getTimestamp());
    }

    @Test
    @DisplayName("A refresh listener receives the repository's failure event with its error")
    void refreshListenerReceivesFailureEvent() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.addFeatureRefreshListener(listener);
        client.initialize();
        RuntimeException error = new RuntimeException("refresh failed");
        FeatureRefreshEvent repositoryEvent = FeatureRefreshEvent.failure(
                error, false, true, 1, FeatureRefreshSource.SSE, FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, 8);

        // When
        repositoryListener(repository).onRefresh(repositoryEvent);

        // Then
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertTrue(event.isLoadedFromCache());
        assertSame(error, event.getError());
        assertEquals(1, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.SSE, event.getSource());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, event.getRefreshStrategy());
        assertEquals(8, event.getDurationMillis());
        assertEquals(repositoryEvent.getTimestamp(), event.getTimestamp());
    }

    @Test
    @DisplayName("A refresh listener is still notified when rebuilding the global context fails")
    void refreshListenerNotifiedWhenGlobalContextRebuildFails() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.addFeatureRefreshListener(listener);
        client.initialize();
        FeatureRefreshListener bridge = repositoryListener(repository);
        when(repository.getFeatureSnapshot()).thenThrow(new IllegalStateException("feature state unavailable"));
        FeatureRefreshEvent event = successEvent();

        // When
        bridge.onRefresh(event);

        // Then
        assertEquals(Collections.singletonList(event), listener.events);
    }

    @Test
    @DisplayName("An unchanged refresh notifies listeners without rebuilding the global context")
    void unchangedRefreshSkipsGlobalContextRebuild() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.addFeatureRefreshListener(listener);
        client.initialize();
        FeatureRefreshListener bridge = repositoryListener(repository);
        clearInvocations(repository);
        FeatureRefreshEvent unchangedEvent = FeatureRefreshEvent.success(
                false, false, 1, FeatureRefreshSource.POLLING, FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, 1);

        // When
        bridge.onRefresh(unchangedEvent);

        // Then
        assertEquals(Collections.singletonList(unchangedEvent), listener.events);
        verify(repository, never()).getFeatureSnapshot();
    }

    @Test
    @DisplayName("A removed refresh listener is no longer notified")
    void removedRefreshListenerIsNotNotified() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.addFeatureRefreshListener(listener);
        client.removeFeatureRefreshListener(listener);
        client.initialize();

        // When
        repositoryListener(repository).onRefresh(successEvent());

        // Then
        assertEquals(Collections.<FeatureRefreshEvent>emptyList(), listener.events);
    }

    @Test
    @DisplayName("A closed refresh subscription, even closed twice, is no longer notified")
    void closedRefreshSubscriptionIsNotNotified() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        FeatureRefreshSubscription subscription = client.subscribeFeatureRefreshListener(listener);
        subscription.close();
        subscription.close();
        client.initialize();

        // When
        repositoryListener(repository).onRefresh(successEvent());

        // Then
        assertEquals(Collections.<FeatureRefreshEvent>emptyList(), listener.events);
    }

    @Test
    @DisplayName("Refresh listeners run on the configured executor, not on the refresh thread")
    void refreshListenersRunOnConfiguredExecutor() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        QueueingExecutor executor = new QueueingExecutor();
        GrowthBookClient client = new GrowthBookClient(optionsBuilder().featureRefreshListenerExecutor(executor).build());
        client.addFeatureRefreshListener(listener);
        client.initialize();
        FeatureRefreshEvent event = successEvent();

        // When
        repositoryListener(repository).onRefresh(event);
        int deliveredBeforeExecutorRan = listener.events.size();
        executor.runAll();

        // Then
        assertEquals(0, deliveredBeforeExecutorRan);
        assertEquals(Collections.singletonList(event), listener.events);
    }

    @Test
    @DisplayName("An event the executor rejects is dropped rather than run on the refresh thread")
    void rejectedEventIsDropped() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        RecordingRefreshListener listener = new RecordingRefreshListener();
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        GrowthBookClient client = new GrowthBookClient(optionsBuilder().featureRefreshListenerExecutor(rejectingExecutor).build());
        client.addFeatureRefreshListener(listener);
        client.initialize();

        // When
        repositoryListener(repository).onRefresh(successEvent());

        // Then
        assertEquals(Collections.<FeatureRefreshEvent>emptyList(), listener.events);
    }

    @Test
    @DisplayName("Shutting down stops the repository but not a caller-supplied listener executor")
    void shutdownLeavesCallerSuppliedExecutorRunning() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        ExecutorService suppliedExecutor = Executors.newSingleThreadExecutor();
        GrowthBookClient client = new GrowthBookClient(optionsBuilder().featureRefreshListenerExecutor(suppliedExecutor).build());
        client.initialize();

        try {
            // When
            client.shutdown();

            // Then
            verify(repository).shutdown();
            assertFalse(suppliedExecutor.isShutdown());
        } finally {
            suppliedExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Initialization reports failure when the repository cannot fetch features")
    void initializationFailsWhenRepositoryCannotFetch() throws FeatureFetchException {
        // Given
        GBFeaturesRepository repository = failingRepository();
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());

        // When
        boolean initialized = client.initialize();

        // Then
        assertFalse(initialized);
        verify(repository).initialize(anyBoolean());
    }

    @Test
    @DisplayName("Initialization can be retried with a fresh repository after a failure")
    void initializationCanBeRetriedAfterFailure() throws FeatureFetchException {
        // Given
        GBFeaturesRepository failedRepository = failingRepository();
        GBFeaturesRepository successfulRepository = stubRepository();
        serve(failedRepository, successfulRepository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());

        // When
        boolean firstAttempt = client.initialize();
        boolean secondAttempt = client.initialize();

        // Then
        assertFalse(firstAttempt);
        assertTrue(secondAttempt);
        verify(failedRepository).initialize(anyBoolean());
        verify(failedRepository).shutdown();
        verify(successfulRepository).initialize(anyBoolean());
    }

    @Test
    @DisplayName("Refreshing features before initialization does not throw")
    void refreshingBeforeInitializationDoesNotThrow() {
        // Given
        GrowthBookClient client = new GrowthBookClient(defaultOptions());

        // When / Then
        assertDoesNotThrow(() -> client.refreshFeatures());
    }

    @Test
    @DisplayName("Refreshing remote evaluation before initialization does not throw")
    void refreshingRemoteEvalBeforeInitializationDoesNotThrow() {
        // Given
        GrowthBookClient client = new GrowthBookClient(defaultOptions());

        // When / Then
        assertDoesNotThrow(() -> client.refreshForRemoteEval(null));
    }

    @Test
    @DisplayName("Setting global attributes parses the JSON onto the options")
    void settingGlobalAttributesParsesJson() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalAttributes("{\"id\":\"user-1\",\"plan\":\"pro\"}");

        // Then
        assertEquals(JSON.gson.fromJson("{\"id\":\"user-1\",\"plan\":\"pro\"}", JsonObject.class), options.getGlobalAttributes());
    }

    @Test
    @DisplayName("Setting global attributes keeps the raw JSON on the options")
    void settingGlobalAttributesKeepsRawJson() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalAttributes("{\"id\":\"user-1\"}");

        // Then
        assertEquals("{\"id\":\"user-1\"}", options.getAttributesJson());
    }

    @Test
    @DisplayName("Setting null global attributes clears the raw JSON and leaves empty attributes")
    void settingNullGlobalAttributesLeavesEmptyAttributes() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalAttributes(null);

        // Then
        assertNull(options.getAttributesJson());
        assertEquals(new JsonObject(), options.getGlobalAttributes());
    }

    @Test
    @DisplayName("Setting global attributes twice keeps only the second value")
    void settingGlobalAttributesTwiceKeepsSecond() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalAttributes("{\"id\":\"first\"}");
        client.setGlobalAttributes("{\"id\":\"second\"}");

        // Then
        assertEquals("{\"id\":\"second\"}", options.getAttributesJson());
        assertEquals(JSON.gson.fromJson("{\"id\":\"second\"}", JsonObject.class), options.getGlobalAttributes());
    }

    @Test
    @DisplayName("Setting global forced features stores the map on the options")
    void settingGlobalForcedFeaturesStoresMap() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);
        Map<String, Object> forcedFeatures = new HashMap<>();
        forcedFeatures.put("dark-mode", true);
        forcedFeatures.put("max-items", 10);

        // When
        client.setGlobalForceFeatures(forcedFeatures);

        // Then
        assertEquals(forcedFeatures, options.getGlobalForcedFeatureValues());
    }

    @Test
    @DisplayName("Setting null global forced features clears them")
    void settingNullGlobalForcedFeaturesClearsThem() {
        // Given
        Options options = Options.builder().globalForcedFeatureValues(new HashMap<>()).build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalForceFeatures(null);

        // Then
        assertNull(options.getGlobalForcedFeatureValues());
    }

    @Test
    @DisplayName("Setting global forced features twice keeps only the second map")
    void settingGlobalForcedFeaturesTwiceKeepsSecond() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);
        Map<String, Object> second = Collections.<String, Object>singletonMap("feature-b", false);

        // When
        client.setGlobalForceFeatures(Collections.<String, Object>singletonMap("feature-a", true));
        client.setGlobalForceFeatures(second);

        // Then
        assertEquals(second, options.getGlobalForcedFeatureValues());
    }

    @Test
    @DisplayName("Setting global forced variations stores the map on the options")
    void settingGlobalForcedVariationsStoresMap() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);
        Map<String, Integer> forcedVariations = new HashMap<>();
        forcedVariations.put("experiment-1", 1);
        forcedVariations.put("experiment-2", 0);

        // When
        client.setGlobalForceVariations(forcedVariations);

        // Then
        assertEquals(forcedVariations, options.getGlobalForcedVariationsMap());
    }

    @Test
    @DisplayName("Setting null global forced variations leaves an empty map")
    void settingNullGlobalForcedVariationsLeavesEmptyMap() {
        // Given
        Options options = Options.builder().globalForcedVariationsMap(new HashMap<>()).build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalForceVariations(null);

        // Then
        assertEquals(Collections.<String, Integer>emptyMap(), options.getGlobalForcedVariationsMap());
    }

    @Test
    @DisplayName("Setting global forced variations twice keeps only the second map")
    void settingGlobalForcedVariationsTwiceKeepsSecond() {
        // Given
        Options options = Options.builder().build();
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        client.setGlobalForceVariations(Collections.singletonMap("exp-a", 1));
        client.setGlobalForceVariations(Collections.singletonMap("exp-b", 0));

        // Then
        assertEquals(Collections.singletonMap("exp-b", 0), options.getGlobalForcedVariationsMap());
    }

    @Test
    @DisplayName("Each client owns its repository, so shutting one down leaves the other running")
    void clientsUseIndependentRepositories() throws FeatureFetchException {
        // Given
        GBFeaturesRepository firstRepository = stubRepository();
        GBFeaturesRepository secondRepository = stubRepository();
        serve(firstRepository, secondRepository);
        GrowthBookClient firstClient = new GrowthBookClient(defaultOptions());
        GrowthBookClient secondClient = new GrowthBookClient(defaultOptions());
        firstClient.initialize();
        secondClient.initialize();

        // When
        firstClient.shutdown();

        // Then
        verify(firstRepository).initialize(anyBoolean());
        verify(secondRepository).initialize(anyBoolean());
        verify(firstRepository).shutdown();
        verify(secondRepository, never()).shutdown();
    }

    @Test
    @DisplayName("Shutting down during repository initialization fails initialization and closes the repository")
    void shutdownDuringInitializationClosesRepository() throws FeatureFetchException {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        doAnswer(invocation -> {
            client.shutdown();
            return null;
        }).when(repository).initialize(anyBoolean());

        // When
        boolean initialized = client.initialize();

        // Then
        assertFalse(initialized);
        verify(repository).initialize(anyBoolean());
        verify(repository).shutdown();
    }

    @Test
    @DisplayName("Shutting down while the repository is being built skips its initialization and closes it")
    void shutdownWhileBuildingRepositorySkipsInitialization() throws FeatureFetchException {
        // Given
        GBFeaturesRepository repository = stubRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = serve(repository).get(0);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        when(builder.build()).thenAnswer(invocation -> {
            client.shutdown();
            return repository;
        });

        // When
        boolean initialized = client.initialize();

        // Then
        assertFalse(initialized);
        verify(repository, never()).onFeaturesRefresh(any());
        verify(repository, never()).addFeatureRefreshListener(any());
        verify(repository, never()).initialize(anyBoolean());
        verify(repository).shutdown();
    }

    @Test
    @DisplayName("Running the same experiment repeatedly with the same result notifies subscribers once")
    void runningSameExperimentNotifiesOnce() {
        // Given
        GrowthBookClient client = new GrowthBookClient();
        RecordingRunCallback callback = new RecordingRunCallback();
        client.subscribe(callback);
        Experiment<String> experiment = Experiment.<String>builder().key("my-experiment").build();

        // When
        client.run(experiment, UserContext.builder().build());
        client.run(experiment, UserContext.builder().build());
        client.run(experiment, UserContext.builder().build());

        // Then
        assertEquals(Collections.singletonList("my-experiment"), callback.experimentKeys);
    }

    @Test
    @DisplayName("A feature with a non-zero default value is on and not off")
    void featureWithNonZeroDefaultIsOn() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        Map<String, Feature<?>> features = TransformationUtil.transformFeatures(TestCasesJsonHelper.getInstance().getDemoFeaturesJson());
        when(repository.getParsedFeatures()).thenReturn(features);
        when(repository.getFeatureSnapshot()).thenReturn(FeatureSnapshot.of("{}", "{}", features, new JsonObject()));
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.initialize();
        UserContext user = user("{ \"user\": \"standard\" }");

        // When
        FeatureResult<Float> result = client.evalFeature("price", Float.class, user);
        Boolean on = client.isOn("price", user);
        Boolean off = client.isOff("price", user);

        // Then
        assertTrue(on);
        assertFalse(off);
        assertTrue(result.isOn());
    }

    @Test
    @DisplayName("Shutting down a client that was never initialized does not touch a repository")
    void shutdownOfUninitializedClientDoesNotTouchRepository() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());

        // When
        client.shutdown();

        // Then
        verify(repository, never()).shutdown();
    }

    @Test
    @DisplayName("Shutting down an initialized client shuts its repository down")
    void shutdownOfInitializedClientShutsRepositoryDown() {
        // Given
        GBFeaturesRepository repository = stubRepository();
        serve(repository);
        GrowthBookClient client = new GrowthBookClient(defaultOptions());
        client.initialize();

        // When
        client.shutdown();

        // Then
        verify(repository).shutdown();
    }

    @Test
    @DisplayName("A real repository refresh notifies listeners off-thread and updates the evaluated features")
    void realRepositoryRefreshNotifiesListenerAndUpdatesFeatures() throws Exception {
        // Given
        serveFeatures("{\"features\":{\"test-feature\":{\"defaultValue\":true}}}");
        GrowthBookClient client = track(new GrowthBookClient(serverOptions()));
        CountDownLatch notified = new CountDownLatch(1);
        AtomicReference<FeatureRefreshEvent> received = new AtomicReference<>();
        client.addFeatureRefreshListener(event -> {
            received.set(event);
            notified.countDown();
        });

        // When
        boolean initialized = client.initialize();

        // Then
        assertTrue(initialized);
        assertTrue(notified.await(5, TimeUnit.SECONDS));
        FeatureRefreshEvent event = received.get();
        assertTrue(event.isSuccessful());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertTrue(event.isFeaturesChanged());
        assertTrue(client.isOn("test-feature", user("{\"id\":\"1\"}")));
    }

    @Test
    @DisplayName("Evaluating a feature preloads sticky assignments for the merged global and user attributes")
    void evaluatingPreloadsStickyAssignmentsForMergedAttributes() {
        // Given
        serve(stubRepository());
        RecordingStickyBucketService stickyBucketService = new RecordingStickyBucketService();
        JsonObject globalAttributes = new JsonObject();
        globalAttributes.addProperty("country", "US");
        Options options = optionsBuilder()
                .stickyBucketService(stickyBucketService)
                .stickyBucketIdentifierAttributes(Arrays.asList("country", "id"))
                .globalAttributes(globalAttributes)
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        client.initialize();

        // When
        client.evalFeature("test-feature", Boolean.class, user("{\"id\":\"1\"}"));

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("country", "US");
        expected.put("id", "1");
        assertEquals(Collections.singletonList(expected), stickyBucketService.lookups);
    }

    @Test
    @DisplayName("Sticky assignments are preloaded under hash-value formatting, skipping unhashable attributes")
    void stickyAssignmentsPreloadedUnderHashValueFormat() {
        // Given
        serve(stubRepository());
        RecordingStickyBucketService stickyBucketService = new RecordingStickyBucketService();
        GrowthBookClient client = new GrowthBookClient(optionsBuilder()
                .stickyBucketService(stickyBucketService)
                .stickyBucketIdentifierAttributes(Arrays.asList("id", "deviceId", "loggedIn", "company"))
                .build());
        client.initialize();
        UserContext user = user("{\"id\":1.0,\"deviceId\":1.5,\"loggedIn\":true,\"company\":\"\"}");

        // When
        client.evalFeature("test-feature", Boolean.class, user);

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("id", "1");
        expected.put("deviceId", "1.5");
        assertEquals(Collections.singletonList(expected), stickyBucketService.lookups);
    }

    @Test
    @DisplayName("Typed feature keys evaluate features fetched from the server and fall back to defaults for unknown keys")
    void typedKeysEvaluateFetchedFeatures() {
        // Given
        serveFeatures("{\"features\":{"
                + "\"new-home\":{\"defaultValue\":true},"
                + "\"theme\":{\"defaultValue\":\"dark\"},"
                + "\"max-items\":{\"defaultValue\":25},"
                + "\"ratio\":{\"defaultValue\":1.5},"
                + "\"weight\":{\"defaultValue\":2}"
                + "}}");
        GrowthBookClient client = track(new GrowthBookClient(serverOptions()));
        assertTrue(client.initialize());
        UserContext user = user("{\"id\":\"1\"}");
        FeatureKey<Boolean> newHome = TypedKey.ofBoolean("new-home");
        FeatureKey<Integer> maxItems = TypedKey.ofInteger("max-items");

        // When
        FeatureResult<Boolean> result = client.getFeature(newHome, user);

        // Then
        assertEquals(Boolean.TRUE, result.getValue());
        assertTrue(result.isOn());
        assertTrue(client.isOn(newHome, user));
        assertFalse(client.isOff(newHome, user));
        assertTrue(client.getBooleanFeature(newHome, user));
        assertEquals("dark", client.getStringFeature(TypedKey.ofString("theme"), "light", user));
        assertEquals(Integer.valueOf(25), client.getIntegerFeature(maxItems, 10, user));
        assertEquals(Integer.valueOf(25), client.getFeatureValue(maxItems, 0, user));
        assertEquals(Double.valueOf(1.5), client.getDoubleFeature(TypedKey.ofDouble("ratio"), 0.0, user));
        assertEquals(Float.valueOf(2.0f), client.getFloatFeature(TypedKey.ofFloat("weight"), 0.0f, user));
        assertFalse(client.getBooleanFeature(TypedKey.ofBoolean("missing"), user));
        assertEquals("light", client.getStringFeature(TypedKey.ofString("missing"), "light", user));
    }

    @Test
    @DisplayName("Reducing the payload simplifies rules for the merged global and user attributes")
    void reducingPayloadSimplifiesRulesForMergedAttributes() {
        // Given
        String featuresJson = "{\"f\": {\"defaultValue\": 0, \"rules\": ["
                + "{\"condition\": {\"env\": \"dev\"}, \"force\": 1},"
                + "{\"condition\": {\"country\": \"US\", \"plan\": \"pro\"}, \"force\": 2},"
                + "{\"condition\": {\"id\": {\"$inGroup\": \"grp\"}}, \"force\": 3}]}}";
        JsonObject savedGroups = JSON.gson.fromJson("{\"grp\": [\"a\"], \"unused\": [\"b\"]}", JsonObject.class);
        GBFeaturesRepository repository = stubRepository();
        when(repository.getFeatureSnapshot()).thenReturn(
                FeatureSnapshot.of(featuresJson, savedGroups.toString(), new HashMap<>(), savedGroups));
        serve(repository);
        Options options = defaultOptions();
        options.setGlobalAttributes("{\"env\": \"dev\", \"country\": \"US\"}");
        GrowthBookClient client = new GrowthBookClient(options);
        client.initialize();

        // When
        JsonObject reduced = client.reducePayload(user("{\"env\": \"prod\"}"));

        // Then
        JsonObject expected = JSON.gson.fromJson("{\"features\": {\"f\": {\"defaultValue\": 0, \"rules\": ["
                + "{\"condition\": {\"$not\": {}}, \"force\": null},"
                + "{\"condition\": {\"plan\": \"pro\"}, \"force\": 2},"
                + "{\"condition\": {\"id\": {\"$inGroup\": \"grp\"}}, \"force\": 3}]}},"
                + "\"experiments\": [], \"savedGroups\": {\"grp\": [\"a\"]}}", JsonObject.class);
        assertEquals(expected, reduced);
    }

    @Test
    @DisplayName("Reducing the payload before initialization gives an empty payload")
    void reducingPayloadBeforeInitializationGivesEmptyPayload() {
        // Given
        GrowthBookClient client = new GrowthBookClient(defaultOptions());

        // When
        JsonObject reduced = client.reducePayload(null, null);

        // Then
        assertEquals(JSON.gson.fromJson("{\"features\": {}, \"experiments\": [], \"savedGroups\": {}}", JsonObject.class), reduced);
    }

    private List<GBFeaturesRepository.GBFeaturesRepositoryBuilder> serve(GBFeaturesRepository... repositories) {
        List<GBFeaturesRepository.GBFeaturesRepositoryBuilder> builders = new ArrayList<>();
        for (GBFeaturesRepository repository : repositories) {
            builders.add(createMockBuilder(repository));
        }
        Iterator<GBFeaturesRepository.GBFeaturesRepositoryBuilder> next = builders.iterator();
        repositoryStatics = mockStatic(GBFeaturesRepository.class);
        repositoryStatics.when(GBFeaturesRepository::builder).thenAnswer(invocation -> next.next());
        return builders;
    }

    private void serveFeatures(String body) {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlEqualTo("/api/features/" + CLIENT_KEY)).willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.X_SSE_SUPPORT.getHeader(), "enabled")
                .withBody(body)));
    }

    private Options serverOptions() {
        return Options.builder()
                .apiHost(wireMock.baseUrl())
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .build();
    }

    private GrowthBookClient track(GrowthBookClient client) {
        clients.add(client);
        return client;
    }

    private static FeatureRefreshListener repositoryListener(GBFeaturesRepository repository) {
        ArgumentCaptor<FeatureRefreshListener> captor = ArgumentCaptor.forClass(FeatureRefreshListener.class);
        verify(repository).addFeatureRefreshListener(captor.capture());
        return captor.getValue();
    }

    private static GBFeaturesRepository stubRepository() {
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        HashMap<String, Feature<?>> features = new HashMap<>();
        features.put("test-feature", new Feature<>());
        when(repository.getInitialized()).thenReturn(true);
        when(repository.getFeaturesJson()).thenReturn("{}");
        when(repository.getSavedGroupsJson()).thenReturn("{}");
        when(repository.getParsedFeatures()).thenReturn(features);
        when(repository.getFeatureSnapshot()).thenReturn(FeatureSnapshot.of("{}", "{}", features, new JsonObject()));
        when(repository.getParsedSavedGroups()).thenReturn(new JsonObject());
        when(repository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        return repository;
    }

    private static GBFeaturesRepository failingRepository() throws FeatureFetchException {
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getInitialized()).thenReturn(false);
        doThrow(new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR))
                .when(repository).initialize(anyBoolean());
        return repository;
    }

    private static Options.OptionsBuilder optionsBuilder() {
        return Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .decryptionKey("test_key")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .featureRefreshListenerExecutor(Runnable::run);
    }

    private static Options defaultOptions() {
        return optionsBuilder().build();
    }

    private static Options forcedFeatureOptions() {
        Map<String, Object> globalForcedFeatures = new HashMap<>();
        globalForcedFeatures.put("forced-feature", true);
        return Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .globalForcedFeatureValues(globalForcedFeatures)
                .build();
    }

    private static UserContext user(String attributesJson) {
        return UserContext.builder().attributesJson(attributesJson).build();
    }

    private static FeatureRefreshEvent successEvent() {
        return FeatureRefreshEvent.success(
                true, false, 1, FeatureRefreshSource.MANUAL, FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, 1);
    }

    private static final class QueueingExecutor implements Executor {
        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            for (Runnable runnable : queued) {
                runnable.run();
            }
            queued.clear();
        }
    }

    private static final class RecordingRunCallback implements ExperimentRunCallback {
        private final List<String> experimentKeys = new ArrayList<>();

        @Override
        public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> result) {
            experimentKeys.add(experiment.getKey());
        }
    }

    private static final class RecordingStickyBucketService implements StickyBucketService {
        private final List<Map<String, String>> lookups = new ArrayList<>();

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            return null;
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            lookups.add(new HashMap<>(attributes));
            return new HashMap<>();
        }
    }
}
