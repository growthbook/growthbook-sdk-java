package growthbook.sdk.java;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * <b>INTERNAL</b>: This convenience class was created to help with the serialization and deserialization of custom types.
 * Some types in the JSON source are tuples. This helps with transforming to and from POJOs.
 * The provided methods use a custom Gson instance that has all required type adapters registered.
 */
@Slf4j
class GrowthBookJsonUtils {
    /**
     * The Gson instance is exposed for convenience.
     */
    public final Gson gson;

    // region Initialization

    private static GrowthBookJsonUtils instance = null;

    private GrowthBookJsonUtils() {
        GsonBuilder gsonBuilder = new GsonBuilder();

        // Namespaces
        gsonBuilder.registerTypeAdapter(Namespace.class, Namespace.getSerializer());
        gsonBuilder.registerTypeAdapter(Namespace.class, Namespace.getDeserializer());

        // BucketRanges
        gsonBuilder.registerTypeAdapter(BucketRange.class, BucketRange.getSerializer());
        gsonBuilder.registerTypeAdapter(BucketRange.class, BucketRange.getDeserializer());

        // FeatureResult
        gsonBuilder.registerTypeAdapter(FeatureResult.class, FeatureResult.getSerializer());

        gson = gsonBuilder.create();
    }

    /**
     * The JSON utils singleton
     * @return an instance of {@link GrowthBookJsonUtils}
     */
    public static GrowthBookJsonUtils getInstance() {
        if (instance == null) {
            instance = new GrowthBookJsonUtils();
        }

        return instance;
    }

    // endregion Initialization


    /**
     * Unwrap an object. If it's not a JsonElement, you'll get the object right back
     * @param o the JSON element to unwrap.
     * @return unwrapped or original object
     */
    public static Object unwrap(final Object o) {
        if (o == null) {
            return null;
        }
        if (!(o instanceof JsonElement)) {
            return o;
        }
        JsonElement e = (JsonElement) o;
        if (e.isJsonNull()) {
            return null;
        } else if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isString()) {
                return p.getAsString();
            } else if (p.isBoolean()) {
                return p.getAsBoolean();
            } else if (p.isNumber()) {
                return unwrapNumber(p.getAsNumber());
            }
        }
        return o;
    }

    private static boolean isPrimitiveNumber(final Number n) {
        return n instanceof Integer ||
                n instanceof Float ||
                n instanceof Double ||
                n instanceof Long ||
                n instanceof BigDecimal ||
                n instanceof BigInteger;
    }

    private static Number unwrapNumber(final Number n) {
        Number unwrapped;

        if (!isPrimitiveNumber(n)) {
            BigDecimal bigDecimal = new BigDecimal(n.toString());
            if (bigDecimal.scale() <= 0) {
                if (bigDecimal.abs().compareTo(new BigDecimal(Integer.MAX_VALUE)) <= 0) {
                    unwrapped = bigDecimal.intValue();
                } else if (bigDecimal.abs().compareTo(new BigDecimal(Long.MAX_VALUE)) <= 0){
                    unwrapped = bigDecimal.longValue();
                } else {
                    unwrapped = bigDecimal;
                }
            } else {
                final double doubleValue = bigDecimal.doubleValue();
                if (BigDecimal.valueOf(doubleValue).compareTo(bigDecimal) != 0) {
                    unwrapped = bigDecimal;
                } else {
                    unwrapped = doubleValue;
                }
            }
        } else {
            unwrapped = n;
        }
        return unwrapped;
    }

    /**
     * A convenience method to help work with types of JSON elements
     * @param element unknown JsonElement
     * @return {@link DataType}
     */
    public static DataType getElementType(@Nullable JsonElement element) {
        try {
            if (element == null) return DataType.UNDEFINED;

            if (element.toString().equals("null")) {
                return DataType.NULL;
            }

            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) return DataType.BOOLEAN;
                if (primitive.isNumber()) return DataType.NUMBER;
                if (primitive.isString()) return DataType.STRING;
            }

            if (element.isJsonArray()) return DataType.ARRAY;
            if (element.isJsonObject()) return DataType.OBJECT;

            return DataType.UNKNOWN;
        } catch (RuntimeException e) {
            log.error("Error getting element type [{}]", element, e);
            return DataType.UNKNOWN;
        }
    }

    /**
     * Given a provided object, which can be a primitive or a serializable class,
     * Will return a JSON element.
     * It will first attempt to detect it as a primitive type. If that doesn't work,
     * it will try to serialize it.
     * If serialization fails, null will be returned.
     *
     * @param object Unknown typed object
     * @return the JSON element that gets created or null if serialization fails
     */
    @Nullable
    public static JsonElement getJsonElementForObject(Object object) {
        try {
            if (object instanceof String) return new JsonPrimitive((String) object);
            if (object instanceof Float) return new JsonPrimitive((Float) object);
            if (object instanceof Integer) return new JsonPrimitive((Integer) object);
            if (object instanceof Double) return new JsonPrimitive((Double) object);
            if (object instanceof Long) return new JsonPrimitive((Long) object);
            if (object instanceof BigDecimal) return new JsonPrimitive((BigDecimal) object);

            return GrowthBookJsonUtils.getInstance().gson.toJsonTree(object);
        } catch (Exception e) {
            log.error("Error getting json element for object [{}]", object, e);
            return null;
        }
    }
}
