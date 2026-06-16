package growthbook.sdk.java.model;

import com.google.gson.JsonObject;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class RequestBodyForRemoteEval {
    private JsonObject attributes;
    private List<List<Object>> forcedFeatures;
    private Map<String, Integer> forcedVariations;
    private String url;

    public RequestBodyForRemoteEval() {
        this(null, null, null, null);
    }

    public RequestBodyForRemoteEval(
            @Nullable JsonObject attributes,
            @Nullable List<List<Object>> forcedFeatures,
            @Nullable Map<String, Integer> forcedVariations,
            @Nullable String url
    ) {
        setAttributes(attributes);
        setForcedFeatures(forcedFeatures);
        setForcedVariations(forcedVariations);
        setUrl(url);
    }

    public void setAttributes(@Nullable JsonObject attributes) {
        this.attributes = attributes == null ? new JsonObject() : attributes;
    }

    public void setForcedFeatures(@Nullable List<List<Object>> forcedFeatures) {
        this.forcedFeatures = forcedFeatures == null ? new ArrayList<>() : forcedFeatures;
    }

    public void setForcedVariations(@Nullable Map<String, Integer> forcedVariations) {
        this.forcedVariations = forcedVariations == null ? new HashMap<>() : forcedVariations;
    }

    public void setUrl(@Nullable String url) {
        this.url = url == null ? "" : url;
    }
}
