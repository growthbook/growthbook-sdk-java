package growthbook.sdk.java.plugin.tracking;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GrowthBookTrackingPluginTest {

    private static final long AWAIT_SECONDS = 5;

    private WireMockServer wireMock;
    private final BlockingQueue<Object> servedRequests = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.addMockServiceRequestListener((request, response) -> servedRequests.add(request));
        wireMock.stubFor(post(urlPathEqualTo("/track")).willReturn(aResponse().withStatus(200)));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("Flushes to the track endpoint with the client key once the batch size is reached")
    void flushesToTrackEndpointWhenBatchSizeIsReached() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(2)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));
        plugin.onExperimentViewed(experiment("exp-2"), experimentResult(1));
        LoggedRequest request = awaitRequests(1).get(0);
        plugin.close();

        // Then
        assertEquals("/track?client_key=sdk-test", request.getUrl());
        assertTrue("application/json; charset=utf-8".equalsIgnoreCase(request.getHeader("Content-Type")),
                request.getHeader("Content-Type"));
        JsonArray events = events(request);
        assertEquals(2, events.size());
        assertEquals("exp-1", events.get(0).getAsJsonObject().getAsJsonObject("properties").get("experimentId").getAsString());
        assertEquals("exp-2", events.get(1).getAsJsonObject().getAsJsonObject("properties").get("experimentId").getAsString());
    }

    @Test
    @DisplayName("Sends the SDK name and resolved version as the User-Agent")
    void sendsSdkNameAndVersionAsUserAgent() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();

        // When
        plugin.onFeatureEvaluated("flag-1", featureResult(FeatureResultSource.DEFAULT_VALUE));
        LoggedRequest request = awaitRequests(1).get(0);
        plugin.close();

        // Then
        assertFalse(SdkMetadata.VERSION.isEmpty());
        assertFalse("unknown".equals(SdkMetadata.VERSION));
        assertEquals("growthbook-java-sdk/" + SdkMetadata.VERSION, request.getHeader("User-Agent"));
    }

    @Test
    @DisplayName("An experiment event matches the track wire contract")
    void experimentEventMatchesTrackWireContract() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(1, "treatment"), attributes());
        JsonObject event = firstEvent(awaitRequests(1).get(0));
        plugin.close();

        // Then
        assertEquals("Experiment Viewed", event.get("event_name").getAsString());
        assertEquals("java", event.get("sdk_language").getAsString());
        assertEquals(SdkMetadata.VERSION, event.get("sdk_version").getAsString());
        JsonObject expectedProperties = new JsonObject();
        expectedProperties.addProperty("experimentId", "exp-1");
        expectedProperties.addProperty("variationId", "treatment");
        expectedProperties.addProperty("hashAttribute", "id");
        expectedProperties.addProperty("hashValue", "user-1");
        assertEquals(expectedProperties, event.getAsJsonObject("properties"));
        assertEquals(attributes(), event.getAsJsonObject("attributes"));
    }

    @Test
    @DisplayName("A feature event matches the track wire contract")
    void featureEventMatchesTrackWireContract() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();
        FeatureResult<String> result = FeatureResult.<String>builder()
                .source(FeatureResultSource.EXPERIMENT)
                .value("x")
                .ruleId("rule-7")
                .experimentResult(experimentResult(1, "treatment"))
                .build();

        // When
        plugin.onFeatureEvaluated("flag-1", result, attributes());
        JsonObject event = firstEvent(awaitRequests(1).get(0));
        plugin.close();

        // Then
        assertEquals("Feature Evaluated", event.get("event_name").getAsString());
        JsonObject expectedProperties = new JsonObject();
        expectedProperties.addProperty("feature", "flag-1");
        expectedProperties.addProperty("value", "x");
        expectedProperties.addProperty("source", "experiment");
        expectedProperties.addProperty("ruleId", "rule-7");
        expectedProperties.addProperty("variationId", "treatment");
        assertEquals(expectedProperties, event.getAsJsonObject("properties"));
        assertEquals(attributes(), event.getAsJsonObject("attributes"));
    }

    @Test
    @DisplayName("A feature event outside an experiment omits the variation id")
    void featureEventWithoutExperimentOmitsVariationId() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();

        // When
        plugin.onFeatureEvaluated("flag-1",
                FeatureResult.<String>builder().source(FeatureResultSource.DEFAULT_VALUE).value("x").build());
        JsonObject properties = firstEvent(awaitRequests(1).get(0)).getAsJsonObject("properties");
        plugin.close();

        // Then
        assertEquals("flag-1", properties.get("feature").getAsString());
        assertFalse(properties.has("variationId"));
    }

    @Test
    @DisplayName("A buffered event keeps the attributes as they were at evaluation time")
    void bufferedEventSnapshotsAttributesAtEvaluation() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();
        JsonObject original = new JsonObject();
        original.addProperty("country", "US");
        JsonObject account = new JsonObject();
        account.addProperty("plan", "gold");
        original.add("account", account);

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0), original);
        original.addProperty("country", "CA");
        account.addProperty("plan", "platinum");
        plugin.close();

        // Then
        JsonObject attributes = firstEvent(awaitRequests(1).get(0)).getAsJsonObject("attributes");
        assertEquals("US", attributes.get("country").getAsString());
        assertEquals("gold", attributes.getAsJsonObject("account").get("plan").getAsString());
    }

    @Test
    @DisplayName("Omits the attributes field when no attributes are provided")
    void omitsAttributesWhenNoneProvided() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));
        JsonObject event = firstEvent(awaitRequests(1).get(0));
        plugin.close();

        // Then
        assertFalse(event.has("attributes"));
    }

    @Test
    @DisplayName("Flushes a partial batch when the batch timer fires")
    void flushesWhenTimerFires() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofMillis(50))
                .build());
        plugin.init();

        // When
        plugin.onFeatureEvaluated("flag-1", featureResult(FeatureResultSource.DEFAULT_VALUE));
        JsonArray events = events(awaitRequests(1).get(0));
        plugin.close();

        // Then
        assertEquals(1, events.size());
        JsonObject event = events.get(0).getAsJsonObject();
        assertEquals("Feature Evaluated", event.get("event_name").getAsString());
        assertEquals("flag-1", event.getAsJsonObject("properties").get("feature").getAsString());
    }

    @Test
    @DisplayName("Close flushes the remaining buffered events synchronously")
    void closeFlushesRemainingEvents() {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));

        // When
        plugin.close();

        // Then
        List<LoggedRequest> requests = trackRequests();
        assertEquals(1, requests.size());
        assertEquals(1, events(requests.get(0)).size());
    }

    @Test
    @DisplayName("Close waits for a batch still pending on a caller-supplied executor")
    void closeWaitsForBatchesOnCallerSuppliedExecutor() {
        // Given
        CountDownLatch closeStarted = new CountDownLatch(1);
        Executor deferredExecutor = task -> new Thread(() -> {
            Uninterruptibles.awaitUninterruptibly(closeStarted);
            task.run();
        }, "deferred-flush").start();
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(1)
                .flushExecutor(deferredExecutor)
                .build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));

        // When
        closeStarted.countDown();
        plugin.close();

        // Then
        assertEquals(1, trackRequests().size());
    }

    @Test
    @DisplayName("Close does not lose a batch the timer drained but has not yet submitted")
    void closeDoesNotLoseTimerTriggeredBatch() throws Exception {
        // Given
        CountDownLatch reachedHandoff = new CountDownLatch(1);
        CountDownLatch releaseHandoff = new CountDownLatch(1);
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofMillis(50))
                .build());
        plugin.timerFlushHandoffHookForTest = () -> {
            reachedHandoff.countDown();
            Uninterruptibles.awaitUninterruptibly(releaseHandoff);
        };
        plugin.init();
        plugin.onFeatureEvaluated("flag-1", featureResult(FeatureResultSource.DEFAULT_VALUE));
        assertTrue(reachedHandoff.await(AWAIT_SECONDS, TimeUnit.SECONDS));

        // When
        Thread closer = new Thread(plugin::close, "close-thread");
        closer.start();
        awaitState(closer, Thread.State.TIMED_WAITING);
        boolean closerWaitedForBatch = closer.isAlive();
        int requestsBeforeSubmit = trackRequests().size();
        releaseHandoff.countDown();
        closer.join(TimeUnit.SECONDS.toMillis(AWAIT_SECONDS));

        // Then
        assertTrue(closerWaitedForBatch);
        assertEquals(0, requestsBeforeSubmit);
        assertFalse(closer.isAlive());
        assertEquals(1, trackRequests().size());
    }

    @Test
    @DisplayName("closeTimeout bounds a final flush against an ingest endpoint that never answers in time")
    void closeTimeoutBoundsSynchronousFinalFlush() {
        // Given
        wireMock.stubFor(post(urlPathEqualTo("/track")).willReturn(aResponse().withStatus(200).withFixedDelay(20_000)));
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .closeTimeout(Duration.ofMillis(300))
                .build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));

        // When / Then
        assertTimeoutPreemptively(Duration.ofSeconds(AWAIT_SECONDS), plugin::close);
    }

    @Test
    @DisplayName("A plugin shared by two GrowthBook instances stops delivering once the first instance is destroyed")
    void sharedPluginStopsDeliveringAfterFirstInstanceIsDestroyed() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        GrowthBook first = growthBookWith(plugin, "{\"id\":\"user-1\"}");
        GrowthBook second = growthBookWith(plugin, "{\"id\":\"user-1\"}");
        first.evalFeature("flag", Boolean.class);
        awaitRequests(1);

        // When
        first.destroy();
        second.evalFeature("flag", Boolean.class);
        second.destroy();

        // Then
        assertEquals(1, trackRequests().size());
    }

    @Test
    @DisplayName("Forwards the evaluated user's attributes from a GrowthBook evaluation")
    void forwardsUserAttributesFromEvaluation() throws Exception {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        GrowthBook growthBook = growthBookWith(plugin, "{\"id\":\"user-7\",\"country\":\"US\"}");

        // When
        growthBook.evalFeature("flag", Boolean.class);
        JsonObject event = firstEvent(awaitRequests(1).get(0));
        growthBook.destroy();

        // Then
        JsonObject expectedAttributes = new JsonObject();
        expectedAttributes.addProperty("id", "user-7");
        expectedAttributes.addProperty("country", "US");
        assertEquals(expectedAttributes, event.getAsJsonObject("attributes"));
    }

    @Test
    @DisplayName("A second close neither throws nor flushes again")
    void secondCloseIsNoOp() {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));
        plugin.close();

        // When
        plugin.close();

        // Then
        assertEquals(1, trackRequests().size());
    }

    @Test
    @DisplayName("Events sent before init are dropped without any request")
    void eventsBeforeInitAreDropped() {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));
        plugin.onFeatureEvaluated("flag-1", featureResult(FeatureResultSource.DEFAULT_VALUE));
        plugin.close();

        // Then
        assertEquals(0, trackRequests().size());
    }

    @Test
    @DisplayName("A blank client key disables the plugin so it never sends a request")
    void blankClientKeyDisablesPlugin() {
        // Given
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(TrackingPluginConfig.builder()
                .ingestorHost(wireMock.baseUrl())
                .batchSize(1)
                .build());
        plugin.init();

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));
        plugin.onFeatureEvaluated("flag-1", featureResult(FeatureResultSource.DEFAULT_VALUE));
        plugin.close();

        // Then
        assertEquals(0, trackRequests().size());
    }

    @Test
    @DisplayName("A failed ingest POST is not thrown and later batches are still delivered")
    void failedIngestPostDoesNotStopLaterBatches() throws Exception {
        // Given
        wireMock.stubFor(post(urlPathEqualTo("/track")).willReturn(aResponse().withStatus(500)));
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();

        // When
        plugin.onExperimentViewed(experiment("exp-1"), experimentResult(0));
        plugin.onExperimentViewed(experiment("exp-2"), experimentResult(0));
        List<LoggedRequest> requests = awaitRequests(2);
        plugin.close();

        // Then
        assertEquals(2, requests.size());
    }

    @Test
    @DisplayName("Rejects a null configuration")
    void rejectsNullConfiguration() {
        // When / Then
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GrowthBookTrackingPlugin.of(null));
        assertEquals("TrackingPluginConfig must not be null", error.getMessage());
    }

    private TrackingPluginConfig.TrackingPluginConfigBuilder configBuilder() {
        return TrackingPluginConfig.builder()
                .ingestorHost(wireMock.baseUrl())
                .clientKey("sdk-test");
    }

    private List<LoggedRequest> trackRequests() {
        return wireMock.findAll(postRequestedFor(urlPathEqualTo("/track")));
    }

    private List<LoggedRequest> awaitRequests(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            assertNotNull(servedRequests.poll(AWAIT_SECONDS, TimeUnit.SECONDS), "expected request #" + (i + 1));
        }
        return trackRequests();
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

    private static GrowthBook growthBookWith(GrowthBookTrackingPlugin plugin, String attributesJson) {
        return new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\":{\"defaultValue\":true}}")
                .attributesJson(attributesJson)
                .plugins(Collections.singletonList(plugin))
                .build());
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder().key(key).build();
    }

    private static ExperimentResult<String> experimentResult(int variation) {
        return experimentResult(variation, String.valueOf(variation));
    }

    private static ExperimentResult<String> experimentResult(int variation, String key) {
        return ExperimentResult.<String>builder()
                .variationId(variation)
                .inExperiment(true)
                .hashUsed(true)
                .value("v-" + variation)
                .key(key)
                .hashAttribute("id")
                .hashValue("user-" + variation)
                .build();
    }

    private static FeatureResult<String> featureResult(FeatureResultSource source) {
        return FeatureResult.<String>builder().source(source).build();
    }

    private static JsonObject attributes() {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "user-1");
        attributes.addProperty("country", "US");
        return attributes;
    }

    private static JsonArray events(LoggedRequest request) {
        return JsonParser.parseString(request.getBodyAsString()).getAsJsonArray();
    }

    private static JsonObject firstEvent(LoggedRequest request) {
        return events(request).get(0).getAsJsonObject();
    }
}
