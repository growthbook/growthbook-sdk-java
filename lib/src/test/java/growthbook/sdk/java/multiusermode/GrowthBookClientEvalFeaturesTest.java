package growthbook.sdk.java.multiusermode;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Covers the multi-user batch API {@link GrowthBookClient#evalFeatures(List, Class, UserContext)}.
 */
class GrowthBookClientEvalFeaturesTest {

    private static final String CLIENT_KEY = "sdk-eval-features-test";

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

    private WireMockServer wireMock;
    private GrowthBookClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlEqualTo("/api/features/" + CLIENT_KEY)).willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.X_SSE_SUPPORT.getHeader(), "enabled")
                .withBody(FEATURES_RESPONSE)));
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        wireMock.stop();
    }

    @Test
    @DisplayName("A batch gives the same value and source per key as evaluating each key alone")
    void batchMatchesSingleEvaluation() {
        // Given
        startClient(null);
        List<String> keys = Arrays.asList("cycA", "child", "parent", "cycB", "plain", "gated", "missing");

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

        // Then
        Map<String, String> expected = new LinkedHashMap<>();
        for (String key : keys) {
            expected.put(key, describe(client.evalFeature(key, Object.class, user())));
        }
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map.Entry<String, FeatureResult<Object>> entry : batch.entrySet()) {
            actual.put(entry.getKey(), describe(entry.getValue()));
        }
        assertEquals(expected, actual);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "child, FORCE",
            "gated, PREREQUISITE",
            "cycA, CYCLIC_PREREQUISITE",
            "plain, DEFAULT_VALUE",
            "missing, UNKNOWN_FEATURE"
    })
    @DisplayName("A batch resolves each kind of feature to its expected source")
    void batchResolvesEachFeatureToExpectedSource(String key, FeatureResultSource expected) {
        // Given
        startClient(null);
        List<String> keys = Arrays.asList("cycA", "child", "parent", "cycB", "plain", "gated", "missing");

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

        // Then
        assertEquals(expected, batch.get(key).getSource());
    }

    @Test
    @DisplayName("Prerequisite results of one key do not leak into later keys of the batch")
    void prerequisiteResultsDoNotLeakBetweenKeys() {
        // Given
        startClient(null);
        List<String> keys = Arrays.asList("gated", "parent", "child", "gated");

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

        // Then
        assertEquals(FeatureResultSource.PREREQUISITE, batch.get("gated").getSource());
        assertNull(batch.get("gated").getValue());
        assertEquals(Boolean.TRUE, batch.get("parent").getValue());
        assertEquals("on", batch.get("child").getValue());
    }

    @Test
    @DisplayName("A batch keeps the input order and evaluates a duplicated key once")
    void batchKeepsInputOrderAndEvaluatesDuplicatesOnce() {
        // Given
        RecordingUsageCallback usage = new RecordingUsageCallback();
        startClient(options -> options.setFeatureUsageCallbackWithUser(usage));
        usage.keys.clear();
        List<String> keys = Arrays.asList("cycA", "child", "parent", "cycB", "plain", "missing", "child");

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(keys, Object.class, user());

        // Then
        assertEquals(Arrays.asList("cycA", "child", "parent", "cycB", "plain", "missing"), new ArrayList<>(batch.keySet()));
        assertEquals(1, Collections.frequency(usage.keys, "plain"));
        assertEquals(1, Collections.frequency(usage.keys, "missing"));
        assertEquals(1, Collections.frequency(usage.keys, "child"));
    }

    @Test
    @DisplayName("An empty key list gives an empty result")
    void emptyKeysGiveEmptyResult() {
        // Given
        startClient(null);

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(Collections.<String>emptyList(), Object.class, user());

        // Then
        assertTrue(batch.isEmpty());
    }

    @Test
    @DisplayName("A null key list gives an empty result")
    void nullKeysGiveEmptyResult() {
        // Given
        startClient(null);

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(null, Object.class, user());

        // Then
        assertTrue(batch.isEmpty());
    }

    @Test
    @DisplayName("A null user context still evaluates the batch")
    void nullUserContextStillEvaluates() {
        // Given
        startClient(null);

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(Collections.singletonList("plain"), Object.class, null);

        // Then
        assertEquals(Collections.singleton("plain"), batch.keySet());
        assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("plain").getSource());
    }

    @Test
    @DisplayName("A null allowUrlOverrides flag does not apply URL overrides")
    void nullAllowUrlOverridesDoesNotApplyUrlOverride() {
        // Given
        startClient(options -> {
            options.setUrl("https://example.com/?gb~plain=99");
            options.setAllowUrlOverrides(null);
        });

        // When
        Map<String, FeatureResult<Object>> batch = client.evalFeatures(Collections.singletonList("plain"), Object.class, user());

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("plain").getSource());
    }

    @Test
    @DisplayName("Enabled allowUrlOverrides applies the URL override")
    void enabledAllowUrlOverridesAppliesUrlOverride() {
        // Given
        startClient(options -> {
            options.setUrl("https://example.com/?gb~plain=99");
            options.setAllowUrlOverrides(true);
        });

        // When
        Map<String, FeatureResult<Integer>> batch = client.evalFeatures(Collections.singletonList("plain"), Integer.class, user());

        // Then
        assertEquals(FeatureResultSource.URL_OVERRIDE, batch.get("plain").getSource());
        assertEquals(99, batch.get("plain").getValue());
    }

    private void startClient(Consumer<Options> customise) {
        Options options = Options.builder()
                .apiHost(wireMock.baseUrl())
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .build();
        if (customise != null) {
            customise.accept(options);
        }
        client = new GrowthBookClient(options);
        assertTrue(client.initialize());
        assertNotEquals(FeatureResultSource.UNKNOWN_FEATURE, client.evalFeature("plain", Object.class, user()).getSource());
    }

    private static UserContext user() {
        return UserContext.builder().attributesJson("{\"id\":\"user-1\"}").build();
    }

    private static String describe(FeatureResult<?> result) {
        return result.getSource() + "=" + result.getValue();
    }

    private static final class RecordingUsageCallback implements FeatureUsageCallbackWithUser {
        private final List<String> keys = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public <T> void onFeatureUsage(String featureKey, FeatureResult<T> result, UserContext userContext) {
            keys.add(featureKey);
        }
    }
}
