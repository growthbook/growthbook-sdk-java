package growthbook.sdk.java.services;

import com.google.gson.*;
import growthbook.sdk.java.models.BucketRange;
import growthbook.sdk.java.models.DataType;
import growthbook.sdk.java.models.Namespace;

import javax.annotation.Nullable;

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
