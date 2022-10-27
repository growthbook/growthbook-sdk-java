package growthbook.sdk.java.services;

import com.google.gson.*;
import growthbook.sdk.java.models.BucketRange;
import growthbook.sdk.java.models.DataType;
import growthbook.sdk.java.models.Namespace;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This convenience class was created to help with the serialization and deserialization of custom types.
 * Some types in the JSON source are tuples. This helps with transforming to and from POJOs.
 * The provided methods use a custom Gson instance that has all required type adapters registered.
 */
public class GrowthBookJsonUtils {
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

        gson = gsonBuilder.create();
    }

    /**
     * @return an instance of {@link GrowthBookJsonUtils}
     */
    public static GrowthBookJsonUtils getInstance() {
        if (instance == null) {
            instance = new GrowthBookJsonUtils();
        }

        return instance;
    }

    // endregion Initialization

    @Nullable
    public static JsonElement getJsonElement(Object o) {
        try {
            JsonElement element = null;

            if (o instanceof Boolean) {
                element = new JsonPrimitive((Boolean) o);
            }
            if (o instanceof Float) {
                element = new JsonPrimitive((Float) o);
            }
            if (o instanceof Integer) {
                element = new JsonPrimitive((Integer) o);
            }
            if (o instanceof String) {
                element = new JsonPrimitive((String) o);
            }

            return element;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

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
            e.printStackTrace();
            return DataType.UNKNOWN;
        }
    }
}
