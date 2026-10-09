package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.plugin.tracking.RecordingHttpServer.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookTrackingPluginTest {

    private RecordingHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new RecordingHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private TrackingPluginConfig.TrackingPluginConfigBuilder configBuilder() {
        return TrackingPluginConfig.builder()
                .ingestorHost(server.baseUrl())
                .clientKey("sdk-test");
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder().key(key).build();
    }

    private static ExperimentResult<String> experimentResult(int variation) {
        return ExperimentResult.<String>builder()
                .variationId(variation)
                .inExperiment(true)
                .hashUsed(true)
                .value("v-" + variation)
                .key(String.valueOf(variation))
                .hashAttribute("id")
                .hashValue("u-" + variation)
                .build();
    }

    private static FeatureResult<String> featureResult(FeatureResultSource source) {
        return FeatureResult.<String>builder().source(source).build();
    }

    private static JsonObject attributes() {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "user-123");
        attributes.addProperty("country", "US");
        return attributes;
    }

    @Test
    void flushesToTrackEndpointWithClientKeyQuery() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(2)
                .batchTimeout(Duration.ofSeconds(30))
                .build());
        plugin.init();

        plugin.onExperimentViewed(experiment("exp1"), experimentResult(0));
        plugin.onExperimentViewed(experiment("exp2"), experimentResult(1));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req, "should have flushed on batch size threshold");
        assertEquals("POST", req.getMethod());
        assertEquals("/track", req.getPath());
        assertEquals("client_key=sdk-test", req.getQuery());
        assertTrue(req.getHeader("User-Agent").startsWith("growthbook-java-sdk/"));
        assertEquals("application/json; charset=utf-8", req.getHeader("Content-Type"));

        JsonArray events = events(req);
        assertEquals(2, events.size());
        JsonObject first = events.get(0).getAsJsonObject();
        assertEquals("Experiment Viewed", first.get("event_name").getAsString());
        assertEquals("exp1", first.getAsJsonObject("properties").get("experimentId").getAsString());

        plugin.close();
    }

    @Test
    void experimentEventMatchesTrackWireContract() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp1"), experimentResult(3), attributes());

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonObject event = firstEvent(req);

        assertEquals("Experiment Viewed", event.get("event_name").getAsString());
        assertEquals("java", event.get("sdk_language").getAsString());
        assertFalse(event.get("sdk_version").getAsString().isEmpty());

        JsonObject properties = event.getAsJsonObject("properties");
        assertEquals("exp1", properties.get("experimentId").getAsString());
        assertEquals("3", properties.get("variationId").getAsString());
        assertEquals("id", properties.get("hashAttribute").getAsString());
        assertEquals("u-3", properties.get("hashValue").getAsString());

        JsonObject attributes = event.getAsJsonObject("attributes");
        assertEquals("user-123", attributes.get("id").getAsString());
        assertEquals("US", attributes.get("country").getAsString());

        plugin.close();
    }

    @Test
    void featureEventMatchesTrackWireContract() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();
        FeatureResult<String> result = FeatureResult.<String>builder()
                .source(FeatureResultSource.EXPERIMENT)
                .value("x")
                .ruleId("rule-7")
                .experimentResult(experimentResult(2))
                .build();
        plugin.onFeatureEvaluated("flag1", result, attributes());

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonObject event = firstEvent(req);

        assertEquals("Feature Evaluated", event.get("event_name").getAsString());
        JsonObject properties = event.getAsJsonObject("properties");
        assertEquals("flag1", properties.get("feature").getAsString());
        assertEquals("x", properties.get("value").getAsString());
        assertEquals("experiment", properties.get("source").getAsString());
        assertEquals("rule-7", properties.get("ruleId").getAsString());
        assertEquals("2", properties.get("variationId").getAsString());

        assertEquals("user-123", event.getAsJsonObject("attributes").get("id").getAsString());

        plugin.close();
    }

    @Test
    void featureEventWithoutExperimentOmitsVariationId() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();
        plugin.onFeatureEvaluated("flag1",
                FeatureResult.<String>builder().source(FeatureResultSource.DEFAULT_VALUE).value("x").build());

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonObject properties = firstEvent(req).getAsJsonObject("properties");
        assertEquals("flag1", properties.get("feature").getAsString());
        assertFalse(properties.has("variationId"),
                "a non-experiment feature evaluation must not carry a variationId");

        plugin.close();
    }

    @Test
    void omitsAttributesWhenNoneProvided() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp1"), experimentResult(0));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonObject event = firstEvent(req);
        assertFalse(event.has("attributes"), "attributes must be omitted when none are provided");

        plugin.close();
    }

    @Test
    void flushesWhenTimerFires() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofMillis(200))
                .build());
        plugin.init();

        plugin.onFeatureEvaluated("flag1", featureResult(FeatureResultSource.DEFAULT_VALUE));

        RecordedRequest req = server.takeRequest(3, TimeUnit.SECONDS);
        assertNotNull(req, "timer-based flush should fire within 3s");
        JsonArray events = events(req);
        assertEquals(1, events.size());
        JsonObject event = events.get(0).getAsJsonObject();
        assertEquals("Feature Evaluated", event.get("event_name").getAsString());
        assertEquals("flag1", event.getAsJsonObject("properties").get("feature").getAsString());

        plugin.close();
    }

    @Test
    void closeFlushesRemainingEvents() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();

        plugin.onExperimentViewed(experiment("exp"), experimentResult(0));
        plugin.close();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req, "close() should flush the final batch synchronously");
        assertEquals(1, events(req).size());
    }

    @Test
    void closeWaitsForBatchesOnCallerSuppliedExecutor() throws Exception {
        server.enqueue(200);

        // Executor that starts the task only after a delay. If close() did NOT wait for
        // the in-flight batch, the POST would not have happened yet when close() returns.
        ScheduledExecutorService delayer = Executors.newSingleThreadScheduledExecutor();
        Executor delayedExecutor = task -> delayer.schedule(task, 300, TimeUnit.MILLISECONDS);
        try {
            GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                    .batchSize(1)
                    .flushExecutor(delayedExecutor)
                    .build());
            plugin.init();

            // batchSize=1 => this submits a batch to the caller-supplied executor immediately.
            plugin.onExperimentViewed(experiment("exp"), experimentResult(0));

            // Must block until the delayed batch actually runs and POSTs.
            plugin.close();

            assertEquals(1, server.getRequestCount(),
                    "close() must wait for the in-flight batch on a caller-supplied executor before returning");
        } finally {
            delayer.shutdownNow();
        }
    }

    @Test
    void closeDoesNotLoseTimerTriggeredBatch() throws Exception {
        server.enqueue(200);

        // Latches to pin the exact interleaving: the timer thread drains + reserves the batch,
        // then parks at the drain-to-submit boundary; close() runs; only then does the timer submit.
        CountDownLatch reachedHandoff = new CountDownLatch(1);
        CountDownLatch releaseHandoff = new CountDownLatch(1);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)                       // large: rely on the timer, not eager flush
                .batchTimeout(Duration.ofMillis(50))  // timer fires quickly
                .build());
        // Park the timer between reserving the batch and submitting it.
        plugin.timerFlushHandoffHookForTest = () -> {
            reachedHandoff.countDown();
            awaitUninterruptibly(releaseHandoff);
        };
        plugin.init();

        plugin.onFeatureEvaluated("flag", featureResult(FeatureResultSource.DEFAULT_VALUE));

        // Wait until the timer has drained the buffer and is parked before submit.
        assertTrue(reachedHandoff.await(5, TimeUnit.SECONDS), "timer flush should have started");

        // Run close() concurrently while the timer is parked at the boundary.
        Thread closer = new Thread(plugin::close, "close-thread");
        closer.start();

        // A correct close() must wait for the reserved batch, so it stays alive here; the buggy
        // version (reserve after releasing the lock) would see nothing in flight and finish now.
        closer.join(500);
        boolean closedBeforeSubmit = !closer.isAlive();

        // Let the timer submit its batch, then let close() finish.
        releaseHandoff.countDown();
        closer.join(5000);

        assertFalse(closedBeforeSubmit,
                "close() returned before the reserved timer batch was submitted — events would be lost");
        assertEquals(1, server.getRequestCount(),
                "the timer-triggered batch must be flushed, not dropped, across close()");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean done = false;
        while (!done) {
            try {
                latch.await();
                done = true;
            } catch (InterruptedException e) {
                // close()'s scheduler.shutdownNow() interrupts this parked timer thread;
                // keep waiting for the test to release it so the interleaving stays fixed.
            }
        }
    }

    @Test
    void closeTimeoutBoundsSynchronousFinalFlush() throws Exception {
        // Server holds the response far longer than closeTimeout; the derived call timeout must cap it.
        server.enqueue(200, 20_000);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)                       // buffer the event; it flushes at close()
                .batchTimeout(Duration.ofSeconds(60))
                .closeTimeout(Duration.ofMillis(300))
                .build());
        plugin.init();
        plugin.onExperimentViewed(experiment("exp"), experimentResult(0));

        long start = System.currentTimeMillis();
        plugin.close(); // the final POST hangs; callTimeout(closeTimeout) must abort it
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000,
                "close() must not block on the hung final POST beyond closeTimeout (took " + elapsed + "ms)");
    }

    @Test
    void sharingOnePluginAcrossInstancesIsUnsupported() throws Exception {
        server.enqueue(200);

        // The same tracking-plugin instance in two SDK instances shares one lifecycle:
        // the first instance's shutdown closes the plugin for the second.
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        GrowthBook a = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\":{\"defaultValue\":true}}")
                .attributesJson("{\"id\":\"u\"}")
                .plugins(Collections.singletonList(plugin))
                .build());
        GrowthBook b = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\":{\"defaultValue\":true}}")
                .attributesJson("{\"id\":\"u\"}")
                .plugins(Collections.singletonList(plugin))
                .build());

        a.evalFeature("flag", Boolean.class);
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "first instance should deliver its event");

        a.destroy(); // closes the shared plugin

        b.evalFeature("flag", Boolean.class);
        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS),
                "second instance shares the now-closed plugin, so its event is not delivered");

        b.destroy();
    }

    @Test
    void forwardsUserAttributesFromEvaluation() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());
        GrowthBook gb = new GrowthBook(GBContext.builder()
                .featuresJson("{\"flag\":{\"defaultValue\":true}}")
                .attributesJson("{\"id\":\"user-7\",\"country\":\"US\"}")
                .plugins(Collections.singletonList(plugin))
                .build());

        gb.evalFeature("flag", Boolean.class);

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req, "evaluation should deliver a feature event");
        JsonObject event = firstEvent(req);
        JsonObject attributes = event.getAsJsonObject("attributes");
        assertNotNull(attributes, "the evaluated user's attributes must reach the event");
        assertEquals("user-7", attributes.get("id").getAsString());
        assertEquals("US", attributes.get("country").getAsString());

        gb.destroy();
    }

    @Test
    void closeIsIdempotent() throws Exception {
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().build());
        plugin.init();
        plugin.close();
        plugin.close();
    }

    @Test
    void eventsBeforeInitAreNoOps() throws Exception {
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().batchSize(1).build());

        // init() has not been called: no resources, event methods must no-op.
        plugin.onExperimentViewed(experiment("exp"), experimentResult(0));
        plugin.onFeatureEvaluated("flag", featureResult(FeatureResultSource.DEFAULT_VALUE));
        plugin.close();

        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS),
                "uninitialized plugin must not hit the network");
    }

    @Test
    void noClientKeyDisablesPlugin() throws Exception {
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(TrackingPluginConfig.builder()
                .ingestorHost(server.baseUrl())
                .batchSize(1)
                .build());
        plugin.init();

        plugin.onExperimentViewed(experiment("exp"), experimentResult(0));
        plugin.onFeatureEvaluated("flag", featureResult(FeatureResultSource.DEFAULT_VALUE));
        plugin.close();

        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS),
                "disabled plugin must not hit the network");
    }

    @Test
    void httpFailureDoesNotThrow() throws Exception {
        server.enqueue(500);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(1)
                .build());
        plugin.init();

        // Must not throw despite the 500.
        plugin.onExperimentViewed(experiment("exp"), experimentResult(0));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        plugin.close();
    }

    @Test
    void ingestorHostTrailingSlashStripped() {
        TrackingPluginConfig cfg = TrackingPluginConfig.builder()
                .ingestorHost("https://example.test/")
                .clientKey("k")
                .build();
        assertEquals("https://example.test", cfg.resolvedIngestorHost());
        assertFalse(cfg.resolvedIngestorHost().endsWith("/"));
    }

    @Test
    void batchSizeIsClampedToMax() {
        TrackingPluginConfig cfg = TrackingPluginConfig.builder()
                .clientKey("k")
                .batchSize(Integer.MAX_VALUE)
                .build();
        assertEquals(TrackingPluginConfig.MAX_BATCH_SIZE, cfg.resolvedBatchSize());
    }

    @Test
    void userAgentDoesNotUseUnknownVersionFallback() throws Exception {
        server.enqueue(200);

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(1)
                .build());
        plugin.init();
        plugin.onFeatureEvaluated("flag", featureResult(FeatureResultSource.DEFAULT_VALUE));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertFalse(req.getHeader("User-Agent").endsWith("/unknown"));
        assertFalse(SdkMetadata.VERSION.isEmpty());
        assertFalse("unknown".equals(SdkMetadata.VERSION));
        plugin.close();
    }

    private static JsonArray events(RecordedRequest req) {
        return JsonParser.parseString(req.bodyUtf8()).getAsJsonArray();
    }

    private static JsonObject firstEvent(RecordedRequest req) {
        return events(req).get(0).getAsJsonObject();
    }
}
