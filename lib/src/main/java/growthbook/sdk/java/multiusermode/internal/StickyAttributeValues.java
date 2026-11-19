package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.util.StringUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>INTERNAL</b>: not part of the supported API. Public only because {@code GrowthBookClient} lives in
 * the parent package — treat its signatures as free to change without a major version bump.
 *
 * Formats sticky-bucket identifier attribute values exactly as evaluation formats hash values
 * ({@code GrowthBookUtils.toHashValue}), so the documents a client reads are keyed the way evaluation
 * writes them: {@code {"id": 1.0}} reads {@code id||1}, and values that cannot be hash values (booleans,
 * zero, empty strings, null, arrays, objects) are left out.
 *
 * <p>Stateless and safe for concurrent use.
 */
public final class StickyAttributeValues {

    private StickyAttributeValues() {
    }

    /**
     * The hash-value form of each named attribute that has one.
     *
     * @param attributes the merged (global and per-user) attributes
     * @param names      the sticky identifier attribute names to look up
     * @return attribute name to hash value; names without a usable value are absent
     */
    public static Map<String, String> of(JsonObject attributes, Collection<String> names) {
        Map<String, String> values = new HashMap<>(Math.max(4, names.size() * 2));
        for (String name : names) {
            String value = hashValue(attributes.get(name));
            if (!value.isEmpty()) {
                values.put(name, value);
            }
        }
        return values;
    }

    /**
     * One attribute value as a hash value, or an empty string when it counts as missing.
     *
     * @param value the attribute value, or null when absent
     * @return the hash value, or an empty string
     */
    public static String hashValue(@Nullable JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            if (number == 0 || Double.isNaN(number) || Double.isInfinite(number)) {
                return "";
            }
            return StringUtils.jsNumberToString(number);
        }
        return "";
    }
}
