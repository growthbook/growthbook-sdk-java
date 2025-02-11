package growthbook.sdk.java.model;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestBodyForRemoteEval {
    @Nullable
    private JsonObject attributes;
    @Nullable
    private List<List<Object>> forcedFeatures;
    @Nullable
    private Map<String, Integer> forcedVariations;
    @Nullable
    private String url;
}