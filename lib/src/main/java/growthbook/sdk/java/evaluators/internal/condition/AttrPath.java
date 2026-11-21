package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * A dot-separated attribute path, split once. Resolves like a JS property chain: object keys,
 * and on arrays only their own keys — a canonical index or {@code length}. Immutable.
 */
public final class AttrPath {

    /** A canonical array index short enough to parse as an int. */
    private static final Pattern ARRAY_INDEX = Pattern.compile("0|[1-9][0-9]{0,8}");

    private final String[] segments;

    private AttrPath(String[] segments) {
        this.segments = segments;
    }

    /**
     * @param path e.g. {@code "address.city"}; empty segments are kept, like JS {@code split},
     *             so {@code "a."} reads key {@code ""} of {@code a} and the empty path reads key
     *             {@code ""}
     * @return the parsed path
     */
    public static AttrPath parse(String path) {
        return new AttrPath(path.split("\\.", -1));
    }

    /**
     * @param attributes the value to start from
     * @return the value at this path, or {@code null} if the path doesn't exist
     */
    @Nullable
    public JsonElement resolve(@Nullable JsonElement attributes) {
        JsonElement element = attributes;
        for (String segment : segments) {
            if (element instanceof JsonObject) {
                element = ((JsonObject) element).get(segment);
            } else if (element instanceof JsonArray) {
                element = arrayOwnKey((JsonArray) element, segment);
            } else {
                return null;
            }
        }
        return element;
    }

    /**
     * The attribute value a condition tests. Like the JS SDK, a missing attribute is {@code null},
     * so {@code $eq: null} and {@code $type: "null"} match it and {@code $nin} passes it.
     *
     * @param attributes the value to start from
     * @return the value at this path, or {@link JsonNull} if the path doesn't exist
     */
    public JsonElement attributeAt(@Nullable JsonElement attributes) {
        JsonElement value = resolve(attributes);
        return value != null ? value : JsonNull.INSTANCE;
    }

    @Nullable
    private static JsonElement arrayOwnKey(JsonArray array, String key) {
        if ("length".equals(key)) {
            return new JsonPrimitive(array.size());
        }
        if (!ARRAY_INDEX.matcher(key).matches()) {
            return null;
        }
        int index = Integer.parseInt(key);
        return index < array.size() ? array.get(index) : null;
    }
}
