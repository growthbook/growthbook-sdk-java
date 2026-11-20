package growthbook.sdk.java.remoteeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.gson.JsonObject;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

class RemoteEvalCacheKeyTest {

    private static final String HOST = "https://gb.example.test";
    private static final String CLIENT_KEY = "sdk-test";

    @Test
    @DisplayName("Builds a key from host, client key and a canonical JSON payload with sorted keys")
    void buildsKeyFromHostClientKeyAndCanonicalPayload() {
        // Given
        JsonObject attributes = json("{\"id\":\"user-1\",\"address\":{\"zip\":\"00000\",\"city\":\"Sample\"},"
                + "\"tags\":[{\"b\":2,\"a\":1},\"x\"]}");
        Map<String, Integer> forcedVariations = new LinkedHashMap<>();
        forcedVariations.put("exp-b", 1);
        forcedVariations.put("exp-a", 0);
        Map<String, Object> forcedFeatures = new LinkedHashMap<>();
        forcedFeatures.put("flag-b", Collections.singletonMap("enabled", true));
        forcedFeatures.put("flag-a", "on");

        // When
        String key = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, attributes, forcedVariations, forcedFeatures,
                "https://app.example.test/page", null);

        // Then
        assertEquals(HOST + "::" + CLIENT_KEY + "||"
                + "{\"ca\":{\"address\":{\"city\":\"Sample\",\"zip\":\"00000\"},\"id\":\"user-1\",\"tags\":[{\"a\":1,\"b\":2},\"x\"]},"
                + "\"fv\":{\"exp-a\":0,\"exp-b\":1},"
                + "\"ff\":{\"flag-a\":\"on\",\"flag-b\":{\"enabled\":true}},"
                + "\"url\":\"https://app.example.test/page\"}", key);
    }

    @Test
    @DisplayName("Gives the same key for attributes that differ only in key order")
    void givesSameKeyRegardlessOfAttributeKeyOrder() {
        // Given
        JsonObject first = json("{\"id\":\"user-1\",\"profile\":{\"plan\":\"pro\",\"age\":30}}");
        JsonObject second = json("{\"profile\":{\"age\":30,\"plan\":\"pro\"},\"id\":\"user-1\"}");

        // When
        String firstKey = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, first, null, null, null, null);
        String secondKey = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, second, null, null, null, null);

        // Then
        assertEquals(firstKey, secondKey);
    }

    @Test
    @DisplayName("Uses only the listed cache key attributes that the user has")
    void usesOnlyListedCacheKeyAttributesThatArePresent() {
        // Given
        JsonObject attributes = json("{\"id\":\"user-1\",\"country\":\"UA\",\"sessionId\":\"s-1\"}");

        // When
        String key = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, attributes, new HashMap<>(), new HashMap<>(), "",
                Arrays.asList("id", "country", "absent"));

        // Then
        assertEquals(HOST + "::" + CLIENT_KEY + "||{\"ca\":{\"country\":\"UA\",\"id\":\"user-1\"},\"fv\":{},\"ff\":{},\"url\":\"\"}", key);
    }

    @ParameterizedTest(name = "attributes = {0}")
    @NullSource
    @DisplayName("Treats missing host, client key, attributes, overrides and URL as empty")
    void treatsMissingInputsAsEmpty(JsonObject attributes) {
        // When
        String key = RemoteEvalCacheKey.fromContext(null, null, attributes, null, null, null, null);

        // Then
        assertEquals("::||{\"ca\":{},\"fv\":{},\"ff\":{},\"url\":\"\"}", key);
    }

    @Test
    @DisplayName("Keeps a null forced feature value in the key")
    void keepsNullForcedFeatureValue() {
        // Given
        Map<String, Object> forcedFeatures = new HashMap<>();
        forcedFeatures.put("flag-a", null);

        // When
        String key = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, new JsonObject(), null, forcedFeatures, null, null);

        // Then
        assertEquals(HOST + "::" + CLIENT_KEY + "||{\"ca\":{},\"fv\":{},\"ff\":{\"flag-a\":null},\"url\":\"\"}", key);
    }

    @Test
    @DisplayName("Gives different keys for different forced variations")
    void givesDifferentKeysForDifferentForcedVariations() {
        // Given
        JsonObject attributes = json("{\"id\":\"user-1\"}");

        // When
        String control = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, attributes,
                Collections.singletonMap("exp-a", 0), null, null, null);
        String variant = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, attributes,
                Collections.singletonMap("exp-a", 1), null, null, null);

        // Then
        assertNotEquals(control, variant);
    }

    @Test
    @DisplayName("from builds the same key as fromContext")
    void fromBuildsSameKeyAsFromContext() {
        // Given
        JsonObject attributes = json("{\"id\":\"user-1\",\"tags\":[\"a\",\"b\"]}");
        Map<String, Object> forcedFeatures = Collections.singletonMap("flag-a", (Object) 3);

        // When
        String viaFrom = RemoteEvalCacheKey.from(HOST, CLIENT_KEY, attributes,
                Collections.singletonMap("exp-a", 1), forcedFeatures, "https://app.example.test", Collections.singletonList("tags"));
        String viaFromContext = RemoteEvalCacheKey.fromContext(HOST, CLIENT_KEY, attributes,
                Collections.singletonMap("exp-a", 1), forcedFeatures, "https://app.example.test", Collections.singletonList("tags"));

        // Then
        assertEquals(viaFromContext, viaFrom);
        assertEquals(HOST + "::" + CLIENT_KEY + "||{\"ca\":{\"tags\":[\"a\",\"b\"]},\"fv\":{\"exp-a\":1},"
                + "\"ff\":{\"flag-a\":3},\"url\":\"https://app.example.test\"}", viaFrom);
    }

    private static JsonObject json(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonObject.class);
    }
}
