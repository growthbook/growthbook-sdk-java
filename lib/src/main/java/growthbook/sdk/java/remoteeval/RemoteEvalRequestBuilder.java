package growthbook.sdk.java.remoteeval;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the remote-eval request payload in the JS/Python SDK wire format.
 */
public final class RemoteEvalRequestBuilder {
    public static final int DEFAULT_CACHE_SIZE = 1000;

    private RemoteEvalRequestBuilder() {
    }

    public static RequestBodyForRemoteEval build(
            @Nullable JsonObject attributes,
            @Nullable Map<String, Object> forcedFeatures,
            @Nullable Map<String, Integer> forcedVariations,
            @Nullable String url
    ) {
        return new RequestBodyForRemoteEval(
                attributes,
                toForcedFeaturesPayload(forcedFeatures),
                forcedVariations,
                normalizeUrl(url)
        );
    }

    public static List<List<Object>> toForcedFeaturesPayload(@Nullable Map<String, Object> forcedFeatures) {
        List<List<Object>> payload = new ArrayList<>();
        if (forcedFeatures == null || forcedFeatures.isEmpty()) {
            return payload;
        }

        for (Map.Entry<String, Object> entry : forcedFeatures.entrySet()) {
            List<Object> forcedFeature = new ArrayList<>();
            forcedFeature.add(entry.getKey());
            forcedFeature.add(entry.getValue());
            payload.add(forcedFeature);
        }
        return payload;
    }

    public static String normalizeUrl(@Nullable String url) {
        return url == null ? "" : url;
    }

    public static int normalizeCacheSize(@Nullable Integer cacheSize) {
        return cacheSize == null ? DEFAULT_CACHE_SIZE : Math.max(0, cacheSize);
    }
}
