package growthbook.sdk.java.multiusermode;

import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link GrowthBookClient#evalFeatures(List, Class, UserContext)} — the multi-user
 * entry point of the batch API. {@code GrowthBookEvalFeaturesTest} covers the single-user one.
 * <p>
 * Features are served by a local HTTP server rather than a mocked repository, so the tests
 * carry no static-mock or shared-cache state between each other.
 */
class GrowthBookClientEvalFeaturesTest {

    private static final String TEST_CLIENT_KEY = "sdk-eval-features-test";

    /**
     * {@code gated} is blocked by a prerequisite that never matches, {@code child} is allowed by one
     * that always matches, and {@code cycA}/{@code cycB} form a prerequisite cycle. Together they
     * exercise the per-feature state that must be reset between keys in a batch.
     */
    private static final String FEATURES_RESPONSE =
            "{\"features\":{"
                    + "\"parent\":{\"defaultValue\":true},"
                    + "\"child\":{\"defaultValue\":\"off\",\"rules\":[{\"parentConditions\":"
                    + "[{\"id\":\"parent\",\"condition\":{\"value\":true},\"gate\":true}],\"force\":\"on\"}]},"
                    + "\"gated\":{\"defaultValue\":\"off\",\"rules\":[{\"parentConditions\":"
                    + "[{\"id\":\"parent\",\"condition\":{\"value\":false},\"gate\":true}],\"force\":\"on\"}]},"
                    + "\"cycA\":{\"defaultValue\":\"a\",\"rules\":[{\"parentConditions\":"
                    + "[{\"id\":\"cycB\",\"condition\":{\"value\":\"b\"}}],\"force\":\"a-forced\"}]},"
                    + "\"cycB\":{\"defaultValue\":\"b\",\"rules\":[{\"parentConditions\":"
                    + "[{\"id\":\"cycA\",\"condition\":{\"value\":\"a\"}}],\"force\":\"b-forced\"}]},"
                    + "\"plain\":{\"defaultValue\":42}"
                    + "}}";

    private static final String ATTRIBUTES_JSON = "{\"id\":\"user-1\"}";

    /**
     * Starts a feature server, builds a client against it and hands the client to {@code assertions}.
     * {@code customise} can adjust the options before the client is created.
     */
    private void withClient(Consumer<Options> customise, Consumer<GrowthBookClient> assertions) throws IOException {
        HttpServer server = startFeatureServer(FEATURES_RESPONSE);
        GrowthBookClient client = null;
        try {
            Options options = Options.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            if (customise != null) {
                customise.accept(options);
            }

            client = new GrowthBookClient(options);
            assertTrue(client.initialize(), "client failed to initialize");
            // Guard the rest of the suite against a silently empty feature set, which would make
            // every assertion below compare unknownFeature against unknownFeature.
            assertNotEquals(FeatureResultSource.UNKNOWN_FEATURE,
                    client.evalFeature("plain", Object.class, user()).getSource(),
                    "features were not loaded from the test server");

            assertions.accept(client);
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    private void withClient(Consumer<GrowthBookClient> assertions) throws IOException {
        withClient(null, assertions);
    }

    private static UserContext user() {
        return UserContext.builder().attributesJson(ATTRIBUTES_JSON).build();
    }

    @Test
    void evalFeatures_matchesSingleEvaluation_forPrerequisitesCyclesDefaultsAndUnknownKeys() throws IOException {
        List<String> keys = Arrays.asList("cycA", "child", "parent", "cycB", "plain", "gated", "missing");

        withClient(client -> {
            Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

            assertEquals(keys.size(), batch.size());
            for (String key : keys) {
                FeatureResult<Object> single = client.evalFeature(key, Object.class, user());
                FeatureResult<Object> batched = batch.get(key);
                assertNotNull(batched, "missing result for " + key);
                assertEquals(single.getValue(), batched.getValue(), "value mismatch for " + key);
                assertEquals(single.getSource(), batched.getSource(), "source mismatch for " + key);
            }

            // Spot-check that the fixtures really exercise the interesting sources.
            assertEquals(FeatureResultSource.FORCE, batch.get("child").getSource());
            assertEquals(FeatureResultSource.PREREQUISITE, batch.get("gated").getSource());
            assertEquals(FeatureResultSource.CYCLIC_PREREQUISITE, batch.get("cycA").getSource());
            assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("plain").getSource());
            assertEquals(FeatureResultSource.UNKNOWN_FEATURE, batch.get("missing").getSource());
        });
    }

    @Test
    void evalFeatures_resetsEvaluationStateBetweenKeys_soPrerequisiteResultsDoNotLeak() throws IOException {
        // "gated" is blocked by "parent"; evaluating it first must not poison the later keys,
        // and evaluating "parent" first must not let its memoized result survive into "gated".
        List<String> keys = Arrays.asList("gated", "parent", "child", "gated");

        withClient(client -> {
            Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

            assertEquals(FeatureResultSource.PREREQUISITE, batch.get("gated").getSource());
            assertEquals(Boolean.TRUE, batch.get("parent").getValue());
            assertEquals("on", batch.get("child").getValue());
        });
    }

    @Test
    void evalFeatures_preservesInputOrder_andEvaluatesEachKeyOnce() throws IOException {
        List<String> keys = Arrays.asList("cycA", "child", "parent", "cycB", "plain", "missing", "child");
        List<String> expectedOrder = Arrays.asList("cycA", "child", "parent", "cycB", "plain", "missing");

        List<String> usage = Collections.synchronizedList(new ArrayList<>());
        // onFeatureUsage is a generic method, so this cannot be a lambda.
        FeatureUsageCallbackWithUser callback = new FeatureUsageCallbackWithUser() {
            @Override
            public <ValueType> void onFeatureUsage(String featureKey, FeatureResult<ValueType> result, UserContext userContext) {
                usage.add(featureKey);
            }
        };

        withClient(options -> options.setFeatureUsageCallbackWithUser(callback), client -> {
            usage.clear(); // drop the usage recorded by the feature-set guard in withClient
            Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

            assertEquals(expectedOrder, new ArrayList<>(batch.keySet()));
            // A duplicate key is evaluated once, so its usage callback fires once per key as well.
            assertEquals(1, Collections.frequency(usage, "plain"));
            assertEquals(1, Collections.frequency(usage, "missing"));
            assertEquals(1, Collections.frequency(usage, "child"));
        });
    }

    @Test
    void evalFeatures_emptyOrNullKeys_returnEmptyMap() throws IOException {
        withClient(client -> {
            assertTrue(client.evalFeatures(Collections.emptyList(), Object.class, user()).isEmpty());
            assertTrue(client.evalFeatures(null, Object.class, user()).isEmpty());
        });
    }

    @Test
    void evalFeatures_nullUserContext_stillEvaluates() throws IOException {
        withClient(client -> {
            Map<String, FeatureResult<Object>> batch =
                    client.evalFeatures(Collections.singletonList("plain"), Object.class, null);

            assertEquals(1, batch.size());
            assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("plain").getSource());
        });
    }

    @Test
    void evalFeatures_nullAllowUrlOverrides_doesNotApplyUrlOverride() throws IOException {
        // allowUrlOverrides is opt-in; a null flag (only reachable through the setter) must be
        // treated as disabled rather than enabling gb~ URL overrides.
        withClient(options -> {
            options.setUrl("https://example.com/?gb~plain=99");
            options.setAllowUrlOverrides(null);
        }, client -> {
            Map<String, FeatureResult<Object>> batch =
                    client.evalFeatures(Collections.singletonList("plain"), Object.class, user());

            assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("plain").getSource());
        });
    }

    @Test
    void evalFeatures_allowUrlOverridesTrue_appliesUrlOverride() throws IOException {
        withClient(options -> {
            options.setUrl("https://example.com/?gb~plain=99");
            options.setAllowUrlOverrides(true);
        }, client -> {
            Map<String, FeatureResult<Integer>> batch =
                    client.evalFeatures(Collections.singletonList("plain"), Integer.class, user());

            assertEquals(FeatureResultSource.URL_OVERRIDE, batch.get("plain").getSource());
            assertEquals(99, batch.get("plain").getValue());
        });
    }

    private static HttpServer startFeatureServer(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/" + TEST_CLIENT_KEY, exchange -> {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.X_SSE_SUPPORT.getHeader(), "enabled");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(responseBytes);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String apiHost(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
