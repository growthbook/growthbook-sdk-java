package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.lang.reflect.Field;
import java.util.Collections;
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

    private static EvaluationContext evalContext(JsonObject attributes) {
        return new EvaluationContext(
                GlobalContext.builder().build(),
                UserContext.builder().attributes(attributes).build(),
                new EvaluationContext.StackContext(),
                Options.builder().build()
        );
    }

    private static void setGlobalContext(GrowthBookClient client, String featuresJson) throws Exception {
        Field globalContext = GrowthBookClient.class.getDeclaredField("globalContext");
        globalContext.setAccessible(true);
        globalContext.set(client, GlobalContext.builder()
                .features(TransformationUtil.transformFeatures(featuresJson))
                .enabled(true)
                .build());
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

    @Test
    void contextAttributesAreSnapshottedWhenEventIsBuffered() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        plugin.init();

        JsonObject attrs = JsonParser.parseString("{\"id\":\"u1\",\"plan\":\"pro\"}").getAsJsonObject();
        plugin.onFeatureEvaluated("flag", featureResult(FeatureResultSource.DEFAULT_VALUE), evalContext(attrs));
        attrs.addProperty("id", "mutated");
        plugin.close();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonObject event = JsonParser.parseString(req.getBody().readUtf8())
                .getAsJsonObject()
                .getAsJsonArray("events")
                .get(0)
                .getAsJsonObject();
        assertEquals("u1", event.getAsJsonObject("attributes").get("id").getAsString());
        assertEquals("pro", event.getAsJsonObject("attributes").get("plan").getAsString());
    }

    @Test
    void singleUserGrowthBookTrackingIncludesContextAttributes() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        GBContext context = GBContext.builder()
                .featuresJson("{\"flag\":{\"defaultValue\":true}}")
                .attributesJson("{\"id\":\"single-user\",\"tier\":\"gold\"}")
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBook growthBook = new GrowthBook(context);

        growthBook.evalFeature("flag", Boolean.class);
        growthBook.close();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonObject event = JsonParser.parseString(req.getBody().readUtf8())
                .getAsJsonObject()
                .getAsJsonArray("events")
                .get(0)
                .getAsJsonObject();
        assertEquals("single-user", event.getAsJsonObject("attributes").get("id").getAsString());
        assertEquals("gold", event.getAsJsonObject("attributes").get("tier").getAsString());
    }

    @Test
    void multiUserGrowthBookClientTrackingUsesMergedScopedAttributes() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(configBuilder()
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build());
        Options options = Options.builder()
                .globalAttributes(JsonParser.parseString("{\"company\":\"acme\",\"id\":\"global\"}").getAsJsonObject())
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        setGlobalContext(client, "{\"flag\":{\"defaultValue\":true}}");

        client.evalFeature("flag", Boolean.class,
                UserContext.builder().attributesJson("{\"id\":\"u1\"}").build());
        client.evalFeature("flag", Boolean.class,
                UserContext.builder().attributesJson("{\"id\":\"u2\"}").build());
        client.close();

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        JsonArray events = JsonParser.parseString(req.getBody().readUtf8())
                .getAsJsonObject()
                .getAsJsonArray("events");
        assertEquals(2, events.size());
        JsonObject firstAttributes = events.get(0).getAsJsonObject().getAsJsonObject("attributes");
        JsonObject secondAttributes = events.get(1).getAsJsonObject().getAsJsonObject("attributes");
        assertEquals("acme", firstAttributes.get("company").getAsString());
        assertEquals("u1", firstAttributes.get("id").getAsString());
        assertEquals("acme", secondAttributes.get("company").getAsString());
        assertEquals("u2", secondAttributes.get("id").getAsString());
    }

    @Test
    void userAgentDoesNotUseUnknownVersionFallback() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

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
}
