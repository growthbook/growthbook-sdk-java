package growthbook.sdk.java.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import growthbook.sdk.java.models.BucketRange;
import growthbook.sdk.java.models.Namespace;

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
}
