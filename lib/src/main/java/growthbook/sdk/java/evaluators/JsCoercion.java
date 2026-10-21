package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JavaScript type coercion for attribute and condition values, so conditions evaluate exactly
 * as in the reference JavaScript SDK. A missing attribute ({@code null}) is treated as JS
 * {@code null}, matching its {@code getPath}.
 */
final class JsCoercion {

    private static final Pattern DECIMAL_LITERAL =
            Pattern.compile("[+-]?(Infinity|(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?)");
    private static final Pattern RADIX_LITERAL = Pattern.compile("0([xX][0-9a-fA-F]+|[oO][0-7]+|[bB][01]+)");

    private JsCoercion() {
    }

    /**
     * JS {@code String(value)}.
     */
    static String toJsString(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonArray()) {
            StringBuilder joined = new StringBuilder();
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    joined.append(',');
                }
                JsonElement item = array.get(i);
                if (!item.isJsonNull()) {
                    joined.append(toJsString(item));
                }
            }
            return joined.toString();
        }
        if (value.isJsonObject()) {
            return "[object Object]";
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return numberToJsString(primitive.getAsDouble());
        }
        return primitive.getAsString();
    }

    /**
     * JS {@code Number(value)}.
     */
    static double toJsNumber(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return 0;
        }
        if (value.isJsonArray()) {
            return stringToJsNumber(toJsString(value));
        }
        if (value.isJsonObject()) {
            return Double.NaN;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? 1 : 0;
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return stringToJsNumber(primitive.getAsString());
    }

    /**
     * JS truthiness ({@code !!value}).
     */
    static boolean isJsTruthy(@Nullable JsonElement value) {
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
     * JS {@code JSON.stringify(value)}, for comparing arrays and plain objects.
     */
    static String toJsJson(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonArray()) {
            StringBuilder json = new StringBuilder("[");
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(toJsJson(array.get(i)));
            }
            return json.append(']').toString();
        }
        if (value.isJsonObject()) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append(new JsonPrimitive(entry.getKey())).append(':').append(toJsJson(entry.getValue()));
            }
            return json.append('}').toString();
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            return Double.isNaN(number) || Double.isInfinite(number) ? "null" : numberToJsString(number);
        }
        return primitive.toString();
    }

    /**
     * JS abstract relational comparison ({@code <}, {@code <=}, {@code >}, {@code >=}): two
     * strings compare by UTF-16 code units; anything else compares as numbers, and NaN never
     * matches.
     *
     * @return the sign of {@code left - right}, or {@code null} when the operands are unordered
     */
    @Nullable
    static Integer compare(@Nullable JsonElement left, @Nullable JsonElement right) {
        if (comparesAsString(left) && comparesAsString(right)) {
            return Integer.signum(toJsString(left).compareTo(toJsString(right)));
        }
        double leftNumber = toJsNumber(left);
        double rightNumber = toJsNumber(right);
        if (Double.isNaN(leftNumber) || Double.isNaN(rightNumber)) {
            return null;
        }
        return leftNumber < rightNumber ? -1 : (leftNumber > rightNumber ? 1 : 0);
    }

    /**
     * Whether JS {@code ToPrimitive} turns this value into a string.
     */
    private static boolean comparesAsString(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonArray() || value.isJsonObject()) {
            return true;
        }
        return value.getAsJsonPrimitive().isString();
    }

    private static double stringToJsNumber(String value) {
        String trimmed = trimJsWhitespace(value);
        if (trimmed.isEmpty()) {
            return 0;
        }
        if (RADIX_LITERAL.matcher(trimmed).matches()) {
            char prefix = Character.toLowerCase(trimmed.charAt(1));
            int radix = prefix == 'x' ? 16 : (prefix == 'o' ? 8 : 2);
            return new BigInteger(trimmed.substring(2), radix).doubleValue();
        }
        if (!DECIMAL_LITERAL.matcher(trimmed).matches()) {
            return Double.NaN;
        }
        if (trimmed.endsWith("Infinity")) {
            return trimmed.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Double.parseDouble(trimmed);
    }

    private static String trimJsWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isJsWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isJsWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isJsWhitespace(char c) {
        return c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r' || c == ' '
                || c == ' ' || c == ' ' || (c >= ' ' && c <= ' ')
                || c == ' ' || c == ' ' || c == ' ' || c == ' '
                || c == '　' || c == '﻿';
    }

    /**
     * JS {@code Number.prototype.toString()}: the shortest digits that round-trip, in plain
     * notation for exponents from -7 to 20 and exponential notation otherwise.
     */
    static String numberToJsString(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0) {
            return "0";
        }
        if (value < 0) {
            return "-" + numberToJsString(-value);
        }

        BigDecimal shortest = null;
        BigDecimal exact = new BigDecimal(value);
        for (int precision = 1; precision <= 17; precision++) {
            BigDecimal candidate = exact.round(new MathContext(precision));
            if (candidate.doubleValue() == value) {
                shortest = candidate;
                break;
            }
        }
        if (shortest == null) {
            shortest = exact.round(new MathContext(17));
        }
        shortest = shortest.stripTrailingZeros();

        String digits = shortest.unscaledValue().toString();
        int k = digits.length();
        int n = k - shortest.scale();

        StringBuilder out = new StringBuilder();
        if (k <= n && n <= 21) {
            out.append(digits);
            for (int i = 0; i < n - k; i++) {
                out.append('0');
            }
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.");
            for (int i = 0; i < -n; i++) {
                out.append('0');
            }
            out.append(digits);
        } else {
            int exponent = n - 1;
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            out.append('e').append(exponent < 0 ? '-' : '+').append(Math.abs(exponent));
        }
        return out.toString();
    }
}
