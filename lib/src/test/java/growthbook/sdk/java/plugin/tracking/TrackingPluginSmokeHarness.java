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
import growthbook.sdk.java.plugin.tracking.RecordingHttpServer.RecordedRequest;

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
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            if ("single".equals(mode) || "both".equals(mode)) {
                JsonArray events = runSingleUserScenario(server);
                printScenario("single", events);
            }
            if ("multi".equals(mode) || "both".equals(mode)) {
                JsonArray events = runMultiUserScenario(server);
                printScenario("multi", events);
            }
            if (!"single".equals(mode) && !"multi".equals(mode) && !"both".equals(mode)) {
                throw new IllegalArgumentException("trackingSmokeMode must be single, multi, or both");
            }
        }
    }

    private static JsonArray runSingleUserScenario(RecordingHttpServer server) throws Exception {
        server.enqueue(200);
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
        growthBook.destroy();

        JsonArray events = readEvents(server);
        requireEventProperty(events, 0, "feature", "flag");
        return events;
    }

    private static JsonArray runMultiUserScenario(RecordingHttpServer server) throws Exception {
        server.enqueue(200);
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
        client.shutdown();

        JsonArray events = readEvents(server);
        requireEventProperty(events, 0, "feature", "flag");
        requireEventProperty(events, 1, "feature", "flag");
        return events;
    }

    private static TrackingPluginConfig config(RecordingHttpServer server) {
        return TrackingPluginConfig.builder()
                .ingestorHost(server.baseUrl())
                .clientKey("sdk-smoke")
                .batchSize(100)
                .batchTimeout(Duration.ofSeconds(60))
                .build();
    }

    private static JsonArray readEvents(RecordingHttpServer server) throws Exception {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new IllegalStateException("Expected tracking plugin POST request");
        }
        if (!"POST".equals(request.getMethod())) {
            throw new IllegalStateException("Expected POST, got " + request.getMethod());
        }
        if (!"/track".equals(request.getPath())) {
            throw new IllegalStateException("Expected /track, got " + request.getPath());
        }
        if (!"client_key=sdk-smoke".equals(request.getQuery())) {
            throw new IllegalStateException("Unexpected query: " + request.getQuery());
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.endsWith("/unknown")) {
            throw new IllegalStateException("Unexpected User-Agent: " + userAgent);
        }
        JsonArray events = JsonParser.parseString(request.bodyUtf8()).getAsJsonArray();
        if (events.size() == 0) {
            throw new IllegalStateException("Expected at least one event");
        }
        return events;
    }

    private static void requireEventProperty(JsonArray events, int eventIndex, String field, String expected) {
        JsonObject properties = events.get(eventIndex).getAsJsonObject().getAsJsonObject("properties");
        if (properties == null || !properties.has(field)) {
            throw new IllegalStateException("Event " + eventIndex + " missing property " + field);
        }
        String actual = properties.get(field).getAsString();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected events[" + eventIndex + "].properties." + field + "=" + expected + ", got " + actual);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setGlobalContext(GrowthBookClient client) throws Exception {
        Field globalContextField = GrowthBookClient.class.getDeclaredField("globalContext");
        globalContextField.setAccessible(true);
        GlobalContext globalContext = GlobalContext.builder()
                .features(TransformationUtil.transformFeatures(FEATURES_JSON))
                .enabled(true)
                .build();
        ((java.util.concurrent.atomic.AtomicReference<GlobalContext>) globalContextField.get(client)).set(globalContext);
    }

    private static void printScenario(String scenario, JsonArray events) {
        System.out.println("=== tracking plugin smoke: " + scenario + " ===");
        System.out.println(events);
    }
}
