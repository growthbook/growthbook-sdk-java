package growthbook.sdk.java.remoteeval;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds deterministic cache keys for remote evaluation payloads.
 */
public final class RemoteEvalCacheKey {
    private static final String CLIENT_SEPARATOR = "::";
    private static final String PAYLOAD_SEPARATOR = "||";
    private static final String ATTRIBUTES_KEY = "ca";
    private static final String FORCED_VARIATIONS_KEY = "fv";
    private static final String URL_KEY = "url";

    private RemoteEvalCacheKey() {
    }

    public static String fromContext(
            String apiHost,
            String clientKey,
            JsonObject attributes,
            @Nullable Map<String, Integer> forcedVariations,
            @Nullable String url,
            @Nullable List<String> cacheKeyAttributes
    ) {
        JsonObject keyPayload = new JsonObject();
        keyPayload.add(ATTRIBUTES_KEY, selectCacheAttributes(attributes, cacheKeyAttributes));
        keyPayload.add(FORCED_VARIATIONS_KEY, toSortedJsonObject(forcedVariations));
        keyPayload.add(URL_KEY, GrowthBookJsonUtils.getInstance().gson.toJsonTree(RemoteEvalRequestBuilder.normalizeUrl(url)));
        return normalize(apiHost) + CLIENT_SEPARATOR + normalize(clientKey)
                + PAYLOAD_SEPARATOR + GrowthBookJsonUtils.getInstance().gson.toJson(keyPayload);
    }

    public static String from(
            String apiHost,
            String clientKey,
            JsonObject attributes,
            @Nullable Map<String, Integer> forcedVariations,
            @Nullable String url,
            @Nullable List<String> cacheKeyAttributes
    ) {
        return fromContext(apiHost, clientKey, attributes, forcedVariations, url, cacheKeyAttributes);
    }

    private static JsonObject selectCacheAttributes(JsonObject attributes, @Nullable List<String> cacheKeyAttributes) {
        JsonObject selected = new JsonObject();
        if (attributes == null) {
            return selected;
        }

        TreeSet<String> keys = new TreeSet<>();
        if (cacheKeyAttributes == null) {
            keys.addAll(attributes.keySet());
        } else {
            keys.addAll(cacheKeyAttributes);
        }

        for (String key : keys) {
            JsonElement value = attributes.get(key);
            if (value != null) {
                selected.add(key, canonicalize(value));
            }
        }
        return selected;
    }

    private static JsonObject toSortedJsonObject(@Nullable Map<String, Integer> values) {
        JsonObject jsonObject = new JsonObject();
        if (values == null || values.isEmpty()) {
            return jsonObject;
        }

        TreeMap<String, Integer> sortedValues = new TreeMap<>(values);
        for (Map.Entry<String, Integer> entry : sortedValues.entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }
        return jsonObject;
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return element == null ? JsonNull.INSTANCE : element.deepCopy();
        }

        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                array.add(canonicalize(item));
            }
            return array;
        }

        JsonObject object = new JsonObject();
        TreeSet<String> keys = new TreeSet<>(element.getAsJsonObject().keySet());
        for (String key : keys) {
            object.add(key, canonicalize(element.getAsJsonObject().get(key)));
        }
        return object;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value;
    }
}
