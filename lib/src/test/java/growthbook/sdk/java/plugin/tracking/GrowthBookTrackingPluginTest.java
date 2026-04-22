package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookTrackingPluginTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private TrackingPluginConfig.TrackingPluginConfigBuilder configBuilder() {
        return TrackingPluginConfig.builder()
                .ingestorHost(server.url("/").toString())
                .clientKey("sdk-test");
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder().key(key).build();
    }

    private static ExperimentResult<String> experimentResult(int variation) {
        return ExperimentResult.<String>builder()
                .variationId(variation)
                .hashAttribute("id")
                .hashValue("u-" + variation)
                .build();
    }

    private static FeatureResult<String> featureResult(FeatureResultSource source) {
        return FeatureResult.<String>builder().source(source).build();
    }

    @Test
    void flushesWhenBatchSizeReached() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

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
        assertEquals("/events", req.getPath());
        assertTrue(req.getHeader("User-Agent").startsWith("growthbook-java-sdk/"));
        assertEquals("application/json; charset=utf-8", req.getHeader("Content-Type"));

        JsonObject body = JsonParser.parseString(req.getBody().readUtf8()).getAsJsonObject();
        assertEquals("sdk-test", body.get("client_key").getAsString());
        JsonArray events = body.getAsJsonArray("events");
        assertEquals(2, events.size());
        assertEquals("experiment_viewed", events.get(0).getAsJsonObject().get("event_type").getAsString());
        assertEquals("exp1", events.get(0).getAsJsonObject().get("experiment_key").getAsString());

        plugin.close();
    }

    @Test
    void flushesWhenTimerFires() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofMillis(200))
                .build());
        plugin.init();

        plugin.onFeatureEvaluated("flag1", featureResult(FeatureResultSource.DEFAULT_VALUE));

        RecordedRequest req = server.takeRequest(3, TimeUnit.SECONDS);
        assertNotNull(req, "timer-based flush should fire within 3s");
        JsonObject body = JsonParser.parseString(req.getBody().readUtf8()).getAsJsonObject();
        JsonArray events = body.getAsJsonArray("events");
        assertEquals(1, events.size());
        assertEquals("feature_evaluated", events.get(0).getAsJsonObject().get("event_type").getAsString());
        assertEquals("flag1", events.get(0).getAsJsonObject().get("feature_key").getAsString());

        plugin.close();
    }

    @Test
    void closeFlushesRemainingEvents() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();

        plugin.onExperimentViewed(experiment("exp"), experimentResult(0));
        plugin.close();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req, "close() should flush the final batch synchronously");
        JsonObject body = JsonParser.parseString(req.getBody().readUtf8()).getAsJsonObject();
        assertEquals(1, body.getAsJsonArray("events").size());
    }

    @Test
    void closeIsIdempotent() throws Exception {
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder().build());
        plugin.close();
        plugin.close();
    }

    @Test
    void noClientKeyDisablesPlugin() throws Exception {
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(TrackingPluginConfig.builder()
                .ingestorHost(server.url("/").toString())
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
        server.enqueue(new MockResponse().setResponseCode(500));

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
}
