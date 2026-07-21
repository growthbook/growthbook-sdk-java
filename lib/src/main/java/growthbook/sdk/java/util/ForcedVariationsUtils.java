package growthbook.sdk.java.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes forced variation values from external inputs into integer variation IDs.
 */
@Slf4j
@UtilityClass
public class ForcedVariationsUtils {

    private static final BigInteger MIN_INTEGER = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX_INTEGER = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal MIN_INTEGER_DECIMAL = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal MAX_INTEGER_DECIMAL = BigDecimal.valueOf(Integer.MAX_VALUE);

    public Map<String, Integer> normalize(@Nullable Map<String, ?> source) {
        Map<String, Integer> normalized = new HashMap<>();
        if (source == null) {
            return normalized;
        }

        for (Map.Entry<String, ?> entry : source.entrySet()) {
            Integer variation = toVariationId(entry.getValue());
            if (variation != null) {
                normalized.put(entry.getKey(), variation);
            } else {
                log.warn("Ignoring invalid forced variation value for key '{}': {}", entry.getKey(), entry.getValue());
            }
        }

        return normalized;
    }

    @Nullable
    private Integer toVariationId(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonElement) {
            return toVariationId((JsonElement) value);
        }
        if (value instanceof Number) {
            return toVariationId((Number) value);
        }

        return null;
    }

    @Nullable
    private Integer toVariationId(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? toInteger(BigDecimal.valueOf(value)) : null;
        }

        return null;
    }

    @Nullable
    private Integer toVariationId(Number value) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return value.intValue();
        }
        if (value instanceof Long) {
            long longValue = value.longValue();
            return isInIntegerRange(longValue) ? (int) longValue : null;
        }
        if (value instanceof BigInteger) {
            BigInteger integer = (BigInteger) value;
            return isInIntegerRange(integer) ? integer.intValue() : null;
        }
        if (value instanceof BigDecimal) {
            return toInteger((BigDecimal) value);
        }
        if (value instanceof Double || value instanceof Float) {
            double doubleValue = value.doubleValue();
            return Double.isFinite(doubleValue) ? toInteger(BigDecimal.valueOf(doubleValue)) : null;
        }

        return null;
    }

    @Nullable
    private Integer toInteger(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 0 || !isInIntegerRange(normalized)) {
            return null;
        }

        return normalized.intValue();
    }

    private boolean isInIntegerRange(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private boolean isInIntegerRange(BigInteger value) {
        return value.compareTo(MIN_INTEGER) >= 0 && value.compareTo(MAX_INTEGER) <= 0;
    }

    private boolean isInIntegerRange(BigDecimal value) {
        return value.compareTo(MIN_INTEGER_DECIMAL) >= 0 && value.compareTo(MAX_INTEGER_DECIMAL) <= 0;
    }
}
