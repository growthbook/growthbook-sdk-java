package growthbook.sdk.java.remoteeval;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RemoteEvalSupportTest {

    private static final String EVAL_PATH = "/api/eval/sdk-test";
    private static final long AWAIT_SECONDS = 5;

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ResponseGate gate = new ResponseGate();
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort().extensions(gate));
        wireMock.start();
        respondWith(featureResponse(true));
    }

    @AfterEach
    void tearDown() {
        gate.release();
        wireMock.stop();
    }

    @Test
    @DisplayName("Posts the user context to the remote eval endpoint and evaluates the response")
    void postsUserContextAndEvaluatesResponse() {
        // Given
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .apiHost(wireMock.baseUrl())
                .clientKey("sdk-test")
                .remoteEval(true)
                .globalForcedVariationsMap(Collections.singletonMap("global-exp", 1))
                .build());
        assertTrue(client.initialize());
        UserContext userContext = UserContext.builder()
                .attributes(jsonObject("{\"id\":\"user-1\"}"))
                .forcedVariationsMap(Collections.singletonMap("user-exp", 2))
                .build();

        // When
        boolean on = client.isOn("remote-feature", userContext);

        // Then
        assertTrue(on);
        List<LoggedRequest> requests = evalRequests();
        assertEquals(1, requests.size());
        JsonObject requestBody = jsonObject(requests.get(0).getBodyAsString());
        assertEquals(jsonObject("{\"id\":\"user-1\"}"), requestBody.getAsJsonObject("attributes"));
        assertEquals(jsonObject("{\"global-exp\":1,\"user-exp\":2}"), requestBody.getAsJsonObject("forcedVariations"));
        assertEquals(new JsonArray(), requestBody.getAsJsonArray("forcedFeatures"));
        assertEquals("", requestBody.get("url").getAsString());
    }

    @Test
    @DisplayName("Reuses the cached response for the same cache key")
    void reusesCachedResponseForSameCacheKey() {
        // Given
        GrowthBookClient client = initializedClient();
        UserContext user = user("user-1");

        // When
        boolean first = client.isOn("remote-feature", user);
        boolean second = client.isOn("remote-feature", user);

        // Then
        assertTrue(first);
        assertTrue(second);
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("cacheKeyAttributes narrows the cache key to the listed attributes")
    void cacheKeyAttributesNarrowTheCacheKey() {
        // Given
        GrowthBookClient client = new GrowthBookClient(remoteEvalOptionsBuilder()
                .cacheKeyAttributes(Collections.singletonList("id"))
                .build());
        assertTrue(client.initialize());

        // When
        client.isOn("remote-feature", userWithAttributes("{\"id\":\"user-1\",\"plan\":\"free\"}"));
        client.isOn("remote-feature", userWithAttributes("{\"id\":\"user-1\",\"plan\":\"enterprise\"}"));

        // Then
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("An empty cacheKeyAttributes list excludes all attributes from the cache key")
    void emptyCacheKeyAttributesExcludeAllAttributesFromCacheKey() {
        // Given
        GrowthBookClient client = new GrowthBookClient(remoteEvalOptionsBuilder()
                .cacheKeyAttributes(Collections.emptyList())
                .build());
        assertTrue(client.initialize());

        // When
        client.isOn("remote-feature", user("user-1"));
        client.isOn("remote-feature", user("user-2"));

        // Then
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("Fetches again when the default cache key changes")
    void fetchesAgainWhenDefaultCacheKeyChanges() {
        // Given
        GrowthBookClient client = initializedClient();

        // When
        client.isOn("remote-feature", user("user-1"));
        client.isOn("remote-feature", user("user-2"));

        // Then
        assertEquals(2, evalRequests().size());
    }

    @Test
    @DisplayName("Coalesces concurrent identical requests into a single network call")
    void coalescesConcurrentIdenticalRequests() throws Exception {
        // Given
        respondGatedWith(featureResponse(true));
        GrowthBookClient client = initializedClient();
        UserContext user = user("user-1");
        List<Boolean> results = new CopyOnWriteArrayList<>();
        Thread first = preloadThread(client, user, results);
        Thread second = preloadThread(client, user, results);
        Thread third = preloadThread(client, user, results);

        // When
        first.start();
        gate.awaitArrival();
        second.start();
        third.start();
        awaitState(second, Thread.State.WAITING);
        awaitState(third, Thread.State.WAITING);
        gate.release();
        joinAll(first, second, third);

        // Then
        assertEquals(Arrays.asList(true, true, true), results);
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("preloadRemoteEval(null) warms the default response without throwing")
    void preloadWithNullContextWarmsDefaultResponse() {
        // Given
        GrowthBookClient client = initializedClient();

        // When
        boolean preloaded = client.preloadRemoteEval(null);

        // Then
        assertTrue(preloaded);
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("Refreshing features clears the remote eval cache")
    void refreshingFeaturesClearsRemoteEvalCache() {
        // Given
        GrowthBookClient client = initializedClient();
        UserContext user = user("user-1");
        client.isOn("remote-feature", user);

        // When
        client.refreshFeatures();
        client.isOn("remote-feature", user);

        // Then
        assertEquals(2, evalRequests().size());
    }

    @Test
    @DisplayName("Forced features are sent in the payload and contribute to the cache key")
    void forcedFeaturesAreSentAndContributeToCacheKey() {
        // Given
        GrowthBookClient client = initializedClient();
        JsonObject attributes = jsonObject("{\"id\":\"user-1\"}");

        // When
        client.isOn("remote-feature", UserContext.builder()
                .attributes(attributes)
                .forcedFeatureValues(forcedFeature(true))
                .build());
        client.isOn("remote-feature", UserContext.builder()
                .attributes(attributes)
                .forcedFeatureValues(forcedFeature(false))
                .build());

        // Then
        List<LoggedRequest> requests = evalRequests();
        assertEquals(2, requests.size());
        JsonArray lastForcedFeatures = jsonObject(requests.get(1).getBodyAsString()).getAsJsonArray("forcedFeatures");
        assertEquals(jsonUtils.gson.fromJson("[[\"overridden-feature\",false]]", JsonArray.class), lastForcedFeatures);
    }

    @Test
    @DisplayName("Reuses the cached response when forced feature values are identical")
    void forcedFeaturesReuseCacheWhenIdentical() {
        // Given
        GrowthBookClient client = initializedClient();
        JsonObject attributes = jsonObject("{\"id\":\"user-1\"}");

        // When
        client.isOn("remote-feature", UserContext.builder()
                .attributes(attributes)
                .forcedFeatureValues(forcedFeature(true))
                .build());
        client.isOn("remote-feature", UserContext.builder()
                .attributes(attributes)
                .forcedFeatureValues(forcedFeature(true))
                .build());

        // Then
        assertEquals(1, evalRequests().size());
    }

    @ParameterizedTest(name = "apiHost = {0}")
    @ValueSource(strings = {"https://cdn.growthbook.io", "cdn.growthbook.io", "GROWTHBOOK.IO"})
    @DisplayName("Refuses to initialize remote eval against a GrowthBook Cloud host")
    void rejectsGrowthBookCloudHost(String apiHost) {
        // Given
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .apiHost(apiHost)
                .clientKey("sdk-test")
                .remoteEval(true)
                .build());

        // When
        boolean initialized = client.initialize();

        // Then
        assertFalse(initialized);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedRemoteEvalOptions")
    @DisplayName("Refuses to initialize remote eval with an incompatible option")
    void rejectsUnsupportedOptions(String description, Options options) {
        // Given
        GrowthBookClient client = new GrowthBookClient(options);

        // When
        boolean initialized = client.initialize();

        // Then
        assertFalse(initialized);
    }

    @Test
    @DisplayName("remoteEvalCacheSize bounds the number of cached responses")
    void cacheSizeBoundsCachedResponses() {
        // Given
        GrowthBookClient client = new GrowthBookClient(remoteEvalOptionsBuilder().remoteEvalCacheSize(1).build());
        assertTrue(client.initialize());

        // When
        client.isOn("remote-feature", user("user-1"));
        client.isOn("remote-feature", user("user-2"));
        client.isOn("remote-feature", user("user-1"));

        // Then
        assertEquals(3, evalRequests().size());
    }

    @Test
    @DisplayName("A negative remoteEvalCacheSize disables caching")
    void negativeCacheSizeDisablesCaching() {
        // Given
        GrowthBookClient client = new GrowthBookClient(remoteEvalOptionsBuilder().remoteEvalCacheSize(-1).build());
        assertTrue(client.initialize());
        UserContext user = user("user-1");

        // When
        client.isOn("remote-feature", user);
        client.isOn("remote-feature", user);

        // Then
        assertEquals(2, evalRequests().size());
    }

    @Test
    @DisplayName("Passes remoteEvalCacheTtlSeconds to the response cache as its hard TTL")
    void passesCacheTtlToResponseCache() {
        // Given
        List<List<?>> constructorArguments = new ArrayList<>();
        GrowthBookClient client = new GrowthBookClient(remoteEvalOptionsBuilder().remoteEvalCacheTtlSeconds(1).build());

        // When
        try (MockedConstruction<RemoteEvalCache> construction = Mockito.mockConstruction(RemoteEvalCache.class,
                (mock, context) -> constructorArguments.add(context.arguments()))) {
            assertTrue(client.initialize());
            client.shutdown();

            // Then
            assertEquals(1, construction.constructed().size());
            assertEquals(Duration.ofSeconds(1), constructorArguments.get(0).get(3));
        }
    }

    @Test
    @DisplayName("Ignores malformed rule.tracks entries")
    void ignoresMalformedTrackEntries() {
        // Given
        respondWith("{\"features\":{\"remote-feature\":{\"rules\":[{\"force\":true,\"tracks\":[{}]}]}},\"savedGroups\":{}}");
        List<String> tracked = new CopyOnWriteArrayList<>();
        GrowthBookClient client = trackingClient(tracked);
        assertTrue(client.initialize());

        // When
        boolean on = client.isOn("remote-feature", user("user-1"));

        // Then
        assertTrue(on);
        assertEquals(Collections.emptyList(), tracked);
    }

    @Test
    @DisplayName("Single-context GrowthBook fetches on initialization and on attribute updates")
    void singleContextFetchesOnInitializationAndAttributeUpdate() {
        // Given
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .apiHost(wireMock.baseUrl())
                .clientKey("sdk-test")
                .remoteEval(true)
                .attributes(jsonObject("{\"id\":\"user-1\"}"))
                .build());
        boolean valueBeforeUpdate = growthBook.getFeatureValue("remote-feature", false);
        int requestsBeforeUpdate = evalRequests().size();

        // When
        growthBook.setAttributes("{\"id\":\"user-2\"}");
        boolean valueAfterUpdate = growthBook.getFeatureValue("remote-feature", false);

        // Then
        assertTrue(valueBeforeUpdate);
        assertEquals(1, requestsBeforeUpdate);
        assertTrue(valueAfterUpdate);
        List<LoggedRequest> requests = evalRequests();
        assertEquals(2, requests.size());
        assertEquals(jsonObject("{\"id\":\"user-2\"}"),
                jsonObject(requests.get(1).getBodyAsString()).getAsJsonObject("attributes"));
    }

    @Test
    @DisplayName("Fires tracking from rule.tracks once per assignment even when the result is served from cache")
    void tracksOncePerCachedAssignment() {
        // Given
        respondWith(trackResponseFor("user-1"));
        List<String> tracked = new CopyOnWriteArrayList<>();
        GrowthBookClient client = trackingClient(tracked);
        assertTrue(client.initialize());
        UserContext user = user("user-1");

        // When
        boolean first = client.isOn("remote-feature", user);
        boolean second = client.isOn("remote-feature", user);

        // Then
        assertTrue(first);
        assertTrue(second);
        assertEquals(1, evalRequests().size());
        assertEquals(Collections.singletonList("track-exp:1:user-1"), tracked);
    }

    @Test
    @DisplayName("Fires tracking independently for distinct users")
    void tracksPerDistinctUser() {
        // Given
        respondInSequence(okJson(trackResponseFor("user-1")), okJson(trackResponseFor("user-2")));
        List<String> tracked = new CopyOnWriteArrayList<>();
        GrowthBookClient client = trackingClient(tracked);
        assertTrue(client.initialize());

        // When
        client.isOn("remote-feature", user("user-1"));
        client.isOn("remote-feature", user("user-2"));

        // Then
        assertEquals(Arrays.asList("track-exp:1:user-1", "track-exp:1:user-2"), tracked);
    }

    @Test
    @DisplayName("Tracking is isolated per client instance")
    void trackingIsIsolatedPerClient() {
        // Given
        respondWith(trackResponseFor("user-1"));
        List<String> trackedA = new CopyOnWriteArrayList<>();
        List<String> trackedB = new CopyOnWriteArrayList<>();
        GrowthBookClient clientA = trackingClient(trackedA);
        GrowthBookClient clientB = trackingClient(trackedB);
        assertTrue(clientA.initialize());
        assertTrue(clientB.initialize());
        UserContext user = user("user-1");

        // When
        clientA.isOn("remote-feature", user);
        clientB.isOn("remote-feature", user);

        // Then
        assertEquals(Collections.singletonList("track-exp:1:user-1"), trackedA);
        assertEquals(Collections.singletonList("track-exp:1:user-1"), trackedB);
    }

    @Test
    @DisplayName("Response caches do not leak between client instances")
    void cacheDoesNotLeakBetweenClients() {
        // Given
        GrowthBookClient clientA = initializedClient();
        GrowthBookClient clientB = initializedClient();
        UserContext user = user("user-1");

        // When
        clientA.isOn("remote-feature", user);
        clientB.isOn("remote-feature", user);

        // Then
        assertEquals(2, evalRequests().size());
    }

    @ParameterizedTest(name = "status {0}, body {1}")
    @MethodSource("failedResponses")
    @DisplayName("A failed response falls back to the default and does not poison the cache")
    void failedResponseFallsBackAndDoesNotPoisonCache(int status, String body) {
        // Given
        respondInSequence(aResponse().withStatus(status).withBody(body), okJson(featureResponse(true)));
        GrowthBookClient client = initializedClient();
        UserContext user = user("user-1");

        // When
        boolean afterFailure = client.isOn("remote-feature", user);
        boolean afterRecovery = client.isOn("remote-feature", user);

        // Then
        assertFalse(afterFailure);
        assertTrue(afterRecovery);
        assertEquals(2, evalRequests().size());
    }

    @Test
    @DisplayName("Unknown fields in the remote eval response are tolerated")
    void toleratesUnknownResponseFields() {
        // Given
        respondWith("{\"features\":{\"remote-feature\":{\"defaultValue\":true,\"unknownField\":42}},"
                + "\"savedGroups\":{},\"meta\":{\"proxy\":\"v1\"}}");
        GrowthBookClient client = initializedClient();

        // When
        boolean on = client.isOn("remote-feature", user("user-1"));

        // Then
        assertTrue(on);
    }

    @Test
    @DisplayName("A response missing the features field falls back to the default")
    void missingFeaturesFieldFallsBack() {
        // Given
        respondWith("{\"savedGroups\":{}}");
        GrowthBookClient client = initializedClient();

        // When
        boolean on = client.isOn("remote-feature", user("user-1"));

        // Then
        assertFalse(on);
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("Shutting down during an in-flight request does not hang and falls back afterwards")
    void shutdownDuringInFlightRequestIsSafe() throws Exception {
        // Given
        respondGatedWith(featureResponse(true));
        GrowthBookClient client = initializedClient();
        UserContext user = user("user-1");
        FutureTask<Boolean> inFlight = new FutureTask<>(() -> client.preloadRemoteEval(user));
        new Thread(inFlight, "remote-eval-preload").start();
        gate.awaitArrival();

        // When
        client.shutdown();
        gate.release();
        inFlight.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        boolean onAfterShutdown = client.isOn("remote-feature", user);

        // Then
        assertFalse(onAfterShutdown);
        assertEquals(1, evalRequests().size());
    }

    @Test
    @DisplayName("Legacy constructors keep remote eval disabled by default")
    void legacyConstructorsKeepRemoteEvalDisabledByDefault() {
        // Given
        Options options = new Options(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );

        // When
        GBContext context = new GBContext(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        // Then
        assertFalse(options.isRemoteEvalEnabled());
        assertEquals(RemoteEvalRequestBuilder.DEFAULT_CACHE_SIZE, options.getRemoteEvalCacheSize());
        assertFalse(context.isRemoteEvalEnabled());
        assertEquals(RemoteEvalRequestBuilder.DEFAULT_CACHE_SIZE, context.getRemoteEvalCacheSize());
    }

    @Test
    @DisplayName("The request body normalizes null fields to empty values")
    void requestBodyNormalizesNullFields() {
        // Given
        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();

        // When
        requestBody.setAttributes(null);
        requestBody.setForcedFeatures(null);
        requestBody.setForcedVariations(null);
        requestBody.setUrl(null);

        // Then
        assertEquals(new JsonObject(), requestBody.getAttributes());
        assertEquals(Collections.emptyList(), requestBody.getForcedFeatures());
        assertEquals(Collections.emptyMap(), requestBody.getForcedVariations());
        assertEquals("", requestBody.getUrl());
    }

    static Stream<Arguments> unsupportedRemoteEvalOptions() {
        String apiHost = "http://localhost:1234";
        return Stream.of(
                Arguments.of("missing clientKey", Options.builder()
                        .apiHost(apiHost)
                        .remoteEval(true)
                        .build()),
                Arguments.of("decryptionKey", Options.builder()
                        .apiHost(apiHost)
                        .clientKey("sdk-test")
                        .decryptionKey("placeholder-decryption-key")
                        .remoteEval(true)
                        .build()),
                Arguments.of("sticky bucketing", Options.builder()
                        .apiHost(apiHost)
                        .clientKey("sdk-test")
                        .stickyBucketService(new InMemoryStickyBucketServiceImpl(new HashMap<>()))
                        .remoteEval(true)
                        .build()),
                Arguments.of("stale-while-revalidate refresh", Options.builder()
                        .apiHost(apiHost)
                        .clientKey("sdk-test")
                        .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                        .remoteEval(true)
                        .build())
        );
    }

    static Stream<Arguments> failedResponses() {
        return Stream.of(
                Arguments.of(500, "{}"),
                Arguments.of(200, "not-json")
        );
    }

    private GrowthBookClient initializedClient() {
        GrowthBookClient client = new GrowthBookClient(remoteEvalOptionsBuilder().build());
        assertTrue(client.initialize());
        return client;
    }

    private Options.OptionsBuilder remoteEvalOptionsBuilder() {
        return Options.builder()
                .apiHost(wireMock.baseUrl())
                .clientKey("sdk-test")
                .remoteEval(true);
    }

    private GrowthBookClient trackingClient(List<String> tracked) {
        return new GrowthBookClient(remoteEvalOptionsBuilder()
                .trackingCallBackWithUser(new RecordingTrackingCallback(tracked))
                .build());
    }

    private void respondWith(String body) {
        wireMock.stubFor(post(urlEqualTo(EVAL_PATH)).willReturn(okJson(body)));
    }

    private void respondGatedWith(String body) {
        wireMock.stubFor(post(urlEqualTo(EVAL_PATH)).willReturn(okJson(body).withTransformers(ResponseGate.NAME)));
    }

    private void respondInSequence(
            ResponseDefinitionBuilder first,
            ResponseDefinitionBuilder then
    ) {
        wireMock.stubFor(post(urlEqualTo(EVAL_PATH))
                .inScenario("sequence")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("after-first")
                .willReturn(first));
        wireMock.stubFor(post(urlEqualTo(EVAL_PATH))
                .inScenario("sequence")
                .whenScenarioStateIs("after-first")
                .willReturn(then));
    }

    private List<LoggedRequest> evalRequests() {
        return wireMock.findAll(postRequestedFor(urlEqualTo(EVAL_PATH)));
    }

    private UserContext user(String id) {
        return userWithAttributes("{\"id\":\"" + id + "\"}");
    }

    private UserContext userWithAttributes(String attributesJson) {
        return UserContext.builder().attributes(jsonObject(attributesJson)).build();
    }

    private JsonObject jsonObject(String json) {
        return jsonUtils.gson.fromJson(json, JsonObject.class);
    }

    private static Map<String, Object> forcedFeature(boolean value) {
        Map<String, Object> forcedFeatures = new HashMap<>();
        forcedFeatures.put("overridden-feature", value);
        return forcedFeatures;
    }

    private static String featureResponse(boolean enabled) {
        return "{\"features\":{\"remote-feature\":{\"defaultValue\":" + enabled + "}},\"savedGroups\":{}}";
    }

    private static String trackResponseFor(String hashValue) {
        return "{\"features\":{\"remote-feature\":{\"defaultValue\":false,\"rules\":[{\"force\":true,\"tracks\":[{"
                + "\"experiment\":{\"key\":\"track-exp\",\"variations\":[false,true]},"
                + "\"result\":{\"variationId\":1,\"inExperiment\":true,\"value\":true,\"hashUsed\":true,"
                + "\"hashAttribute\":\"id\",\"hashValue\":\"" + hashValue + "\",\"featureId\":\"remote-feature\",\"key\":\"1\"}"
                + "}]}]}},\"savedGroups\":{}}";
    }

    private static Thread preloadThread(GrowthBookClient client, UserContext user, List<Boolean> results) {
        return new Thread(() -> results.add(client.preloadRemoteEval(user)), "remote-eval-preload");
    }

    private static void joinAll(Thread... threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(AWAIT_SECONDS));
            assertFalse(thread.isAlive(), thread.getName() + " did not finish");
        }
    }

    private static void awaitState(Thread thread, Thread.State state) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (thread.getState() != state) {
            if (System.nanoTime() > deadline) {
                fail("Thread " + thread.getName() + " never reached " + state + ", was " + thread.getState());
            }
            Thread.yield();
        }
    }

    private static final class RecordingTrackingCallback implements TrackingCallbackWithUser {
        private final List<String> tracked;

        RecordingTrackingCallback(List<String> tracked) {
            this.tracked = tracked;
        }

        @Override
        public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> experimentResult, UserContext userContext) {
            tracked.add(experiment.getKey() + ":" + experimentResult.getVariationId()
                    + ":" + userContext.getAttributes().get("id").getAsString());
        }
    }

    private static final class ResponseGate extends ResponseDefinitionTransformer {
        static final String NAME = "response-gate";

        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        @Override
        public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource files, Parameters parameters) {
            arrived.countDown();
            Uninterruptibles.awaitUninterruptibly(released, AWAIT_SECONDS, TimeUnit.SECONDS);
            return responseDefinition;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }

        void awaitArrival() throws InterruptedException {
            assertTrue(arrived.await(AWAIT_SECONDS, TimeUnit.SECONDS), "request never reached the server");
        }

        void release() {
            released.countDown();
        }
    }
}
