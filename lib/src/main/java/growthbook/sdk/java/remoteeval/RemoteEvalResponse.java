package growthbook.sdk.java.remoteeval;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * Parsed response returned by the GrowthBook remote evaluation endpoint.
 */
@Getter
public class RemoteEvalResponse {
    private final Map<String, Feature<?>> features;
    private final JsonObject savedGroups;

    public RemoteEvalResponse(Map<String, Feature<?>> features, JsonObject savedGroups) {
        this.features = features == null ? Collections.emptyMap() : features;
        this.savedGroups = savedGroups == null ? new JsonObject() : savedGroups;
    }
}
