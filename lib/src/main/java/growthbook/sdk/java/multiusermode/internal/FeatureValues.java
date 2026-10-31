package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonParseException;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Reads an evaluated feature value as the type a caller asked for, falling back to the caller's
 * default when the value is missing or of another type. Numbers arrive as {@link Double} or
 * {@link Long} from JSON and are narrowed as requested. Pure functions.
 */
@Slf4j
public final class FeatureValues {

    private FeatureValues() {
    }

    public static Boolean asBoolean(@Nullable Object value, Boolean defaultValue) {
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    public static String asString(@Nullable Object value, String defaultValue) {
        return value instanceof String ? (String) value : defaultValue;
    }

    public static Float asFloat(@Nullable Object value, Float defaultValue) {
        return isJsonNumber(value) ? ((Number) value).floatValue() : defaultValue;
    }

    public static Integer asInteger(@Nullable Object value, Integer defaultValue) {
        return isJsonNumber(value) ? ((Number) value).intValue() : defaultValue;
    }

    public static Double asDouble(@Nullable Object value, Double defaultValue) {
        return isJsonNumber(value) ? ((Number) value).doubleValue() : defaultValue;
    }

    /**
     * @return the value converted to {@code type} through its JSON form, or the default when the
     * value is missing or doesn't deserialize into that type
     */
    public static <T> T convert(@Nullable Object value, T defaultValue, Class<T> type) {
        if (value == null) {
            return defaultValue;
        }
        try {
            GrowthBookJsonUtils json = GrowthBookJsonUtils.getInstance();
            T converted = json.gson.fromJson(json.gson.toJsonTree(value), type);
            return converted != null ? converted : defaultValue;
        } catch (JsonParseException | IllegalArgumentException e) {
            log.error("Feature value {} does not deserialize into {}: {}", value, type.getName(), e.getMessage());
            return defaultValue;
        }
    }

    private static boolean isJsonNumber(@Nullable Object value) {
        return value instanceof Double || value instanceof Long;
    }
}
