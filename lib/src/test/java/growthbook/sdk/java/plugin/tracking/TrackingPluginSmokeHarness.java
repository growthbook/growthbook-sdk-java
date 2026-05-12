package growthbook.sdk.java.plugin.tracking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Local smoke harness for inspecting the real tracking plugin HTTP payload.
 *
 * <p>Run with {@code ./gradlew :lib:runTrackingPluginSmoke}. Set
 * {@code -PtrackingSmokeMode=single|multi|both} to narrow the scenario.
 */
public final class TrackingPluginSmokeHarness {

    private static final String FEATURES_JSON = "{\"flag\":{\"defaultValue\":true}}";

    private TrackingPluginSmokeHarness() {
    }

    public static void main(String[] args) throws Exception {
        String mode = System.getProperty("trackingSmokeMode", "both");
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            if ("single".equals(mode) || "both".equals(mode)) {
                JsonObject body = runSingleUserScenario(server);
                printScenario("single", body);
            }
            if ("multi".equals(mode) || "both".equals(mode)) {
                JsonObject body = runMultiUserScenario(server);
                printScenario("multi", body);
            }
            if (!"single".equals(mode) && !"multi".equals(mode) && !"both".equals(mode)) {
                throw new IllegalArgumentException("trackingSmokeMode must be single, multi, or both");
            }
        }
    }

    private static JsonObject runSingleUserScenario(MockWebServer server) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(config(server));

        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson("{\"id\":\"single-smoke\",\"tier\":\"gold\"}")
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBook growthBook = new GrowthBook(context);
        growthBook.evalFeature("flag", Boolean.class);
        growthBook.run(Experiment.<String>builder()
                .key("smoke-experiment")
                .variations(new ArrayList<>(Arrays.asList("A", "B")))
                .coverage(1f)
                .build());
        growthBook.close();

        JsonObject body = readBody(server);
        requireAttribute(body, 0, "id", "single-smoke");
        requireHeaderShape(body);
        return body;
    }

    private static JsonObject runMultiUserScenario(MockWebServer server) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        GrowthBookTrackingPlugin plugin = GrowthBookTrackingPlugin.of(config(server));

        Options options = Options.builder()
                .globalAttributes(JsonParser.parseString("{\"company\":\"acme\"}").getAsJsonObject())
                .plugins(Collections.singletonList(plugin))
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        setGlobalContext(client);

        client.evalFeature("flag", Boolean.class,
                UserContext.builder().attributesJson("{\"id\":\"multi-smoke-1\"}").build());
        client.evalFeature("flag", Boolean.class,
                UserContext.builder().attributesJson("{\"id\":\"multi-smoke-2\"}").build());
        client.close();

        JsonObject body = readBody(server);
        requireAttribute(body, 0, "company", "acme");
        requireAttribute(body, 0, "id", "multi-smoke-1");
        requireAttribute(body, 1, "id", "multi-smoke-2");
        requireHeaderShape(body);
        return body;
    }

    private static TrackingPluginConfig config(MockWebServer server) {
        return TrackingPluginConfig.builder()
                .ingestorHost(server.url("/").toString())
                .clientKey("sdk-smoke")
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build();
    }

    private static JsonObject readBody(MockWebServer server) throws Exception {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new IllegalStateException("Expected tracking plugin POST request");
        }
        if (!"POST".equals(request.getMethod())) {
            throw new IllegalStateException("Expected POST, got " + request.getMethod());
        }
        if (!"/events".equals(request.getPath())) {
            throw new IllegalStateException("Expected /events, got " + request.getPath());
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.endsWith("/unknown")) {
            throw new IllegalStateException("Unexpected User-Agent: " + userAgent);
        }
        return JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
    }

    private static void requireHeaderShape(JsonObject body) {
        if (!"sdk-smoke".equals(body.get("client_key").getAsString())) {
            throw new IllegalStateException("Unexpected client_key: " + body.get("client_key"));
        }
        JsonArray events = body.getAsJsonArray("events");
        if (events == null || events.size() == 0) {
            throw new IllegalStateException("Expected at least one event");
        }
    }

    private static void requireAttribute(JsonObject body, int eventIndex, String key, String expected) {
        JsonObject attributes = body.getAsJsonArray("events")
                .get(eventIndex)
                .getAsJsonObject()
                .getAsJsonObject("attributes");
        String actual = attributes.get(key).getAsString();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected attributes." + key + "=" + expected + ", got " + actual);
        }
    }

    private static void setGlobalContext(GrowthBookClient client) throws Exception {
        Field globalContext = GrowthBookClient.class.getDeclaredField("globalContext");
        globalContext.setAccessible(true);
        globalContext.set(client, GlobalContext.builder()
                .features(TransformationUtil.transformFeatures(FEATURES_JSON))
                .enabled(true)
                .build());
    }

    private static void printScenario(String scenario, JsonObject body) {
        System.out.println("=== tracking plugin smoke: " + scenario + " ===");
        System.out.println(body);
    }
}
