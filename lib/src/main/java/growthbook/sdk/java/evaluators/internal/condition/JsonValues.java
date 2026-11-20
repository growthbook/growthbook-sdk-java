package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.DataType;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import javax.annotation.Nullable;

/**
 * JSON value semantics the condition operators share, matching JavaScript: strict equality,
 * membership and ordering. Pure functions.
 */
public final class JsonValues {

    private JsonValues() {
    }

    public static boolean isString(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    public static boolean isBoolean(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    /**
     * JavaScript truthiness of a JSON value: absent, null, false, 0, NaN and "" are falsy.
     */
    public static boolean isTruthy(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (!value.isJsonPrimitive()) {
            return true;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            return number != 0 && !Double.isNaN(number);
        }
        return !primitive.getAsString().isEmpty();
    }

    /**
     * @return the boolean a value holds, or {@code null} when it isn't a boolean
     */
    @Nullable
    public static Boolean asBoolean(@Nullable JsonElement value) {
        return isBoolean(value) ? value.getAsBoolean() : null;
    }

    @Nullable
    public static String stringField(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return isString(value) ? value.getAsString() : null;
    }

    /**
     * Tests whether {@code actual} (a primitive, or any element of an array) is contained in the
     * {@code expected} array.
     */
    public static boolean isIn(@Nullable JsonElement actual, JsonArray expected, boolean caseInsensitive) {
        if (actual == null) {
            return false;
        }
        if (!actual.isJsonArray()) {
            return containsElement(expected, actual, caseInsensitive);
        }
        for (JsonElement item : actual.getAsJsonArray()) {
            if (containsElement(expected, item, caseInsensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code $eq}/{@code $ne} equality: the same type and the same value, with no coercion, like
     * JavaScript's {@code ===}. A missing attribute equals nothing, and arrays and objects are never
     * equal here.
     */
    static boolean strictEquals(@Nullable JsonElement actual, JsonElement expected) {
        if (actual == null) {
            return false;
        }
        if (actual.isJsonNull()) {
            return expected.isJsonNull();
        }
        return actual.isJsonPrimitive() && expected.isJsonPrimitive()
                && primitivesEqual(actual.getAsJsonPrimitive(), expected.getAsJsonPrimitive());
    }

    /**
     * Whether two primitives have the same type and value. Numbers compare as {@code double}, so
     * {@code 1} equals {@code 1.0} and {@code -0.0} equals {@code 0}; a number never equals a string
     * or boolean.
     */
    static boolean primitivesEqual(JsonPrimitive a, JsonPrimitive b) {
        if (a.isNumber() && b.isNumber()) {
            return a.getAsDouble() == b.getAsDouble();
        }
        if (a.isString() && b.isString()) {
            return a.getAsString().equals(b.getAsString());
        }
        if (a.isBoolean() && b.isBoolean()) {
            return a.getAsBoolean() == b.getAsBoolean();
        }
        return false;
    }

    /**
     * The type two values can be ordered by: both numbers, or both strings.
     *
     * @return {@link DataType#NUMBER} or {@link DataType#STRING}, or {@code null} when the values
     * can't be ordered against each other
     */
    @Nullable
    static DataType orderableType(@Nullable JsonElement actual, @Nullable JsonElement expected) {
        DataType type = GrowthBookJsonUtils.getElementType(actual);
        boolean orderable = (type == DataType.NUMBER || type == DataType.STRING)
                && type == GrowthBookJsonUtils.getElementType(expected);
        return orderable ? type : null;
    }

    /**
     * Orders two doubles the way JavaScript's {@code <}/{@code >} do: {@code -0.0} equals
     * {@code 0.0}. Deliberately not {@link Double#compare}, which orders {@code -0.0} below
     * {@code 0.0} and would make {@code -0.0 $gte 0} false. Callers rule out {@code NaN} first.
     */
    static int compareDoubles(double left, double right) {
        if (left < right) {
            return -1;
        }
        return left > right ? 1 : 0;
    }

    private static boolean containsElement(JsonArray candidates, JsonElement value, boolean caseInsensitive) {
        for (JsonElement candidate : candidates) {
            if (elementsEqual(value, candidate, caseInsensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Membership equality, like JS {@code ===}: nulls match each other, primitives match by type and
     * value (so {@code 1} equals {@code 1.0}), and arrays and objects never match.
     */
    private static boolean elementsEqual(JsonElement a, JsonElement b, boolean caseInsensitive) {
        if (a == null || b == null || a.isJsonNull() || b.isJsonNull()) {
            return (a == null || a.isJsonNull()) && (b == null || b.isJsonNull());
        }
        if (!a.isJsonPrimitive() || !b.isJsonPrimitive()) {
            return false;
        }
        JsonElement left = caseInsensitive ? caseFold(a) : a;
        JsonElement right = caseInsensitive ? caseFold(b) : b;
        return primitivesEqual(left.getAsJsonPrimitive(), right.getAsJsonPrimitive());
    }

    private static JsonElement caseFold(JsonElement value) {
        if (isString(value)) {
            return new JsonPrimitive(value.getAsString().toLowerCase());
        }
        return value;
    }
}
