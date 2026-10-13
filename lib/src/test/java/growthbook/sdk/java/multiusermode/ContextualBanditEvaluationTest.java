package growthbook.sdk.java.multiusermode;

import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contextual bandit consumption in the multi-user client path: a bandit feature rule resolves
 * per-segment weights from the {@code contextualBandits} payload, buckets accordingly, and surfaces
 * {@code leafId}/{@code variationWeights}/{@code banditVersion} on the experiment result.
 *
 * <p>Weights of {@code [1,0]} / {@code [0,1]} make the assigned variation deterministic regardless
 * of the hash, so assertions do not depend on bucketing internals. A local HTTP server serves the
 * payload so the real fetch, parse and evaluation pipeline runs end to end.
 */
class ContextualBanditEvaluationTest {

    private static final String TEST_CLIENT_KEY = "sdk-contextual-bandit-test";

    private static final String PAYLOAD_WITH_BANDIT =
            "{\"features\":{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"bandit-1\","
                    + "\"contextualVariations\":[\"control\",\"treatment\"],"
                    + "\"hashAttribute\":\"id\""
                    + "}]}},"
                    + "\"contextualBandits\":{\"bandit-1\":{\"banditVersion\":3,\"contexts\":["
                    + "{\"leafId\":0,\"condition\":{\"country\":\"US\"},\"weights\":[1.0,0.0]},"
                    + "{\"leafId\":1,\"condition\":{\"country\":\"CA\"},\"weights\":[0.0,1.0]}"
                    + "]}}}";

    private static final String PAYLOAD_MISSING_BANDIT =
            "{\"features\":{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"absent\","
                    + "\"contextualVariations\":[\"control\",\"treatment\"],"
                    + "\"hashAttribute\":\"id\""
                    + "}]}}}";

    private static final String PAYLOAD_EMPTY_CONTEXTS =
            "{\"features\":{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"bandit-1\","
                    + "\"contextualVariations\":[\"control\",\"treatment\"],"
                    + "\"hashAttribute\":\"id\""
                    + "}]}},"
                    + "\"contextualBandits\":{\"bandit-1\":{\"banditVersion\":7,\"contexts\":[]}}}";

    private static final String PAYLOAD_CATCH_ALL_LEAF =
            "{\"features\":{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"bandit-1\","
                    + "\"contextualVariations\":[\"control\",\"treatment\"],"
                    + "\"hashAttribute\":\"id\""
                    + "}]}},"
                    + "\"contextualBandits\":{\"bandit-1\":{\"banditVersion\":2,\"contexts\":["
                    + "{\"leafId\":0,\"condition\":{\"country\":\"US\"},\"weights\":[1.0,0.0]},"
                    + "{\"leafId\":9,\"condition\":{},\"weights\":[0.0,1.0]}"
                    + "]}}}";

    @Test
    @DisplayName("Verify: a matching leaf applies its weights and records leafId/variationWeights/banditVersion")
    void matchingLeafAppliesWeightsAndRecordsMetadata() throws IOException {
        withClient(PAYLOAD_WITH_BANDIT, client -> {
            FeatureResult<String> result = client.evalFeature("banner", String.class, userContext("{\"id\":\"u1\",\"country\":\"US\"}"));

            assertEquals("control", result.getValue());
            assertTrue(result.getExperimentResult().getInExperiment());
            assertEquals(Integer.valueOf(0), result.getExperimentResult().getLeafId());
            assertEquals(Integer.valueOf(3), result.getExperimentResult().getBanditVersion());
            assertEquals(Arrays.asList(1.0f, 0.0f), result.getExperimentResult().getVariationWeights());
        });
    }

    @Test
    @DisplayName("Verify: a different segment matches a different leaf and gets that leaf's weights")
    void differentSegmentMatchesDifferentLeaf() throws IOException {
        withClient(PAYLOAD_WITH_BANDIT, client -> {
            FeatureResult<String> result = client.evalFeature("banner", String.class, userContext("{\"id\":\"u1\",\"country\":\"CA\"}"));

            assertEquals("treatment", result.getValue());
            assertEquals(Integer.valueOf(1), result.getExperimentResult().getLeafId());
            assertEquals(Arrays.asList(0.0f, 1.0f), result.getExperimentResult().getVariationWeights());
        });
    }

    @Test
    @DisplayName("Verify: no matching leaf falls back to leafId -1 with equal weights, user still in experiment")
    void noMatchingLeafFallsBackToEqualWeights() throws IOException {
        withClient(PAYLOAD_WITH_BANDIT, client -> {
            FeatureResult<String> result = client.evalFeature("banner", String.class, userContext("{\"id\":\"u1\",\"country\":\"FR\"}"));

            assertTrue(result.getExperimentResult().getInExperiment());
            assertEquals(Integer.valueOf(-1), result.getExperimentResult().getLeafId());
            assertEquals(Integer.valueOf(3), result.getExperimentResult().getBanditVersion());
            assertEquals(Arrays.asList(0.5f, 0.5f), result.getExperimentResult().getVariationWeights());
        });
    }

    @Test
    @DisplayName("Verify: a missing bandit ref evaluates as an ordinary experiment with no bandit metadata")
    void missingBanditRefLeavesNoMetadata() throws IOException {
        withClient(PAYLOAD_MISSING_BANDIT, client -> {
            FeatureResult<String> result = client.evalFeature("banner", String.class, userContext("{\"id\":\"u1\",\"country\":\"US\"}"));

            assertTrue(result.getExperimentResult().getInExperiment());
            assertNull(result.getExperimentResult().getLeafId());
            assertNull(result.getExperimentResult().getBanditVersion());
            assertNull(result.getExperimentResult().getVariationWeights());
        });
    }

    @Test
    @DisplayName("Verify: a definition with no leaves falls back to leafId -1 and equal weights")
    void emptyContextsFallsBackToEqualWeights() throws IOException {
        withClient(PAYLOAD_EMPTY_CONTEXTS, client -> {
            FeatureResult<String> result = client.evalFeature("banner", String.class, userContext("{\"id\":\"u1\",\"country\":\"US\"}"));

            assertTrue(result.getExperimentResult().getInExperiment());
            assertEquals(Integer.valueOf(-1), result.getExperimentResult().getLeafId());
            assertEquals(Integer.valueOf(7), result.getExperimentResult().getBanditVersion());
            assertEquals(Arrays.asList(0.5f, 0.5f), result.getExperimentResult().getVariationWeights());
        });
    }

    @Test
    @DisplayName("Verify: a leaf with an empty condition matches any user the earlier leaves miss")
    void emptyConditionLeafActsAsCatchAll() throws IOException {
        withClient(PAYLOAD_CATCH_ALL_LEAF, client -> {
            FeatureResult<String> result = client.evalFeature("banner", String.class, userContext("{\"id\":\"u1\",\"country\":\"FR\"}"));

            assertEquals("treatment", result.getValue());
            assertEquals(Integer.valueOf(9), result.getExperimentResult().getLeafId());
            assertEquals(Arrays.asList(0.0f, 1.0f), result.getExperimentResult().getVariationWeights());
        });
    }

    private void withClient(String payload, Consumer<GrowthBookClient> assertions) throws IOException {
        HttpServer server = startFeatureServer(payload);
        GrowthBookClient client = null;
        try {
            Options options = Options.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            client = new GrowthBookClient(options);
            assertTrue(client.initialize(), "client should initialize from the served payload");
            assertions.accept(client);
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    private static UserContext userContext(String attributesJson) {
        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson.fromJson(attributesJson, JsonObject.class);
        return UserContext.builder().attributes(attributes).build();
    }

    private static HttpServer startFeatureServer(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/" + TEST_CLIENT_KEY, exchange -> {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.X_SSE_SUPPORT.getHeader(), "disabled");
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
