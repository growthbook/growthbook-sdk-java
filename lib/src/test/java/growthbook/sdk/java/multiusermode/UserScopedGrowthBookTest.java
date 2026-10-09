package growthbook.sdk.java.multiusermode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.TypedKey;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserScopedGrowthBookTest {

    private static final String TEST_CLIENT_KEY = "sdk-test";
    private static final String FEATURES =
            "{\"features\":{"
                    + "\"flag-on\":{\"defaultValue\":true},"
                    + "\"count\":{\"defaultValue\":7}"
                    + "}}";

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                .willReturn(aResponse().withStatus(200).withBody(FEATURES)));
    }

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    @DisplayName("Verify: a scoped instance evaluates features against its bound user context without an explicit context argument")
    void delegatesFeatureEvaluationToBoundContext() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserContext user = UserContext.builder().attributesJson("{\"id\":\"1\"}").build();
            UserScopedGrowthBook scoped = client.createScopedInstance(user);

            // When & Then
            assertTrue(scoped.isOn("flag-on"));
            assertFalse(scoped.isOff("flag-on"));
            assertEquals(Integer.valueOf(7), scoped.getFeatureValue("count", 0, Integer.class));
            assertTrue(scoped.evalFeature("flag-on", Boolean.class).isOn());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: forced feature values on the scoped instance override the evaluated value")
    void forcedFeatureValuesOverrideEvaluatedValue() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());

            // When
            Map<String, Object> forced = new HashMap<>();
            forced.put("flag-on", false);
            scoped.setForcedFeatures(forced);

            // Then
            assertFalse(scoped.isOn("flag-on"));
            assertTrue(scoped.isOff("flag-on"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: forced variations on the scoped instance select the variation for an inline experiment")
    void forcedVariationsSelectVariationForInlineExperiment() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());
            Map<String, Integer> forced = new HashMap<>();
            forced.put("checkout-exp", 1);
            scoped.setForcedVariations(forced);

            // When
            ExperimentResult<String> result = scoped.run(inlineExperiment());

            // Then
            assertEquals("treatment", result.getValue());
            assertEquals(Integer.valueOf(1), result.getVariationId());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: a per-user tracking callback takes precedence over the client-level callback")
    void perUserTrackingCallbackTakesPrecedenceOverClientCallback() throws IOException {
        // Given
        CountingTrackingCallback clientCallback = new CountingTrackingCallback();
        CountingTrackingCallback userCallback = new CountingTrackingCallback();

        GrowthBookClient client = initializedClient(clientCallback);
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());
            scoped.setTrackingCallback(userCallback);

            // When
            scoped.run(inlineExperiment());

            // Then
            assertEquals(1, userCallback.calls.get());
            assertEquals(0, clientCallback.calls.get());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: evaluations fall back to the client-level tracking callback when no per-user callback is set")
    void fallsBackToClientTrackingCallbackWhenNoPerUserCallback() throws IOException {
        // Given
        CountingTrackingCallback clientCallback = new CountingTrackingCallback();

        GrowthBookClient client = initializedClient(clientCallback);
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());

            // When
            scoped.run(inlineExperiment());

            // Then
            assertEquals(1, clientCallback.calls.get());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: updateAttributes merges into the bound context, preserving existing attributes")
    void updateAttributesMergesIntoBoundContext() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());

            // When
            JsonObject extra = new JsonObject();
            extra.addProperty("country", "UA");
            scoped.updateAttributes(extra);

            // Then
            JsonObject merged = scoped.getUserContext().getAttributes();
            assertEquals("1", merged.get("id").getAsString());
            assertEquals("UA", merged.get("country").getAsString());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: mutating one scoped instance does not affect another created from the same client")
    void scopedInstancesAreIndependent() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook first = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());
            UserScopedGrowthBook second = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"2\"}").build());

            // When
            first.setForcedFeatures(Collections.singletonMap("flag-on", (Object) false));

            // Then
            assertFalse(first.isOn("flag-on"));
            assertTrue(second.isOn("flag-on"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: a scoped instance created from a null context binds an empty context and evaluates against defaults")
    void nullContextBindsEmptyContext() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            // When
            UserScopedGrowthBook scoped = client.createScopedInstance(null);

            // Then
            assertNotNull(scoped.getUserContext());
            assertTrue(scoped.isOn("flag-on"));
            assertFalse(scoped.isOn("missing-flag"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: updateAttributes with a null argument is a no-op and keeps the same bound context")
    void updateAttributesWithNullIsNoOp() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());
            UserContext before = scoped.getUserContext();

            // When
            scoped.updateAttributes(null);

            // Then
            assertEquals("1", scoped.getUserContext().getAttributes().get("id").getAsString());
            assertNotNull(before);
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: setting attributes replaces the bound context rather than mutating the supplied one")
    void mutatorsRebuildBoundContext() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());
            UserContext before = scoped.getUserContext();

            // When
            scoped.setURL("https://example.test/checkout");

            // Then
            UserContext after = scoped.getUserContext();
            assertNotSame(before, after);
            assertEquals("https://example.test/checkout", after.getUrl());
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: typed feature keys resolve against the bound context through the scoped instance")
    void typedFeatureKeysDelegateToBoundContext() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());
            FeatureKey<Boolean> flagOn = TypedKey.ofBoolean("flag-on");
            FeatureKey<Integer> count = TypedKey.ofInteger("count");

            // When & Then
            assertTrue(scoped.getFeature(flagOn).isOn());
            assertTrue(scoped.isOn(flagOn));
            assertFalse(scoped.isOff(flagOn));
            assertTrue(scoped.getBooleanFeature(flagOn));
            assertEquals(Integer.valueOf(7), scoped.getIntegerFeature(count, 0));
            assertEquals(Integer.valueOf(7), scoped.getFeatureValue(count, 0));
            // Unknown key falls back to the supplied default.
            assertFalse(scoped.getBooleanFeature(TypedKey.ofBoolean("missing"), false));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: setAttributes replaces the bound attributes rather than merging them")
    void setAttributesReplacesBoundAttributes() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\",\"country\":\"UA\"}").build());

            // When
            JsonObject replacement = new JsonObject();
            replacement.addProperty("id", "2");
            scoped.setAttributes(replacement);

            // Then
            JsonObject attributes = scoped.getUserContext().getAttributes();
            assertEquals("2", attributes.get("id").getAsString());
            assertFalse(attributes.has("country"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Verify: running an experiment through the scoped instance notifies the client's experiment subscribers")
    void runNotifiesClientExperimentSubscribers() throws IOException {
        // Given
        GrowthBookClient client = initializedClient();
        try {
            CountingRunCallback callback = new CountingRunCallback();
            client.subscribe(callback);
            UserScopedGrowthBook scoped = client.createScopedInstance(
                    UserContext.builder().attributesJson("{\"id\":\"1\"}").build());

            // When
            scoped.run(inlineExperiment());

            // Then
            assertEquals(1, callback.calls.get());
        } finally {
            client.shutdown();
        }
    }

    private static Experiment<String> inlineExperiment() {
        return Experiment.<String>builder()
                .key("checkout-exp")
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .build();
    }

    private GrowthBookClient initializedClient() {
        return initializedClient(null);
    }

    private GrowthBookClient initializedClient(TrackingCallbackWithUser trackingCallback) {
        Options options = Options.builder()
                .apiHost("http://localhost:" + wireMock.port())
                .clientKey(TEST_CLIENT_KEY)
                .isCacheDisabled(true)
                .trackingCallBackWithUser(trackingCallback)
                .featureRefreshListenerExecutor(Runnable::run)
                .build();
        GrowthBookClient client = new GrowthBookClient(options);
        assertTrue(client.initialize());
        return client;
    }

    private static final class CountingTrackingCallback implements TrackingCallbackWithUser {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                        ExperimentResult<ValueType> experimentResult,
                                        UserContext userContext) {
            calls.incrementAndGet();
        }
    }

    private static final class CountingRunCallback implements ExperimentRunCallback {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public <ValueType> void onRun(Experiment<ValueType> experiment,
                                      ExperimentResult<ValueType> experimentResult) {
            calls.incrementAndGet();
        }
    }
}
