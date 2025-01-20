package growthbook.sdk.java;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payload {
    @Nullable
    private JsonObject attributes;
    @Nullable
    private List<List<Object>> forcedFeatures;
    @Nullable
    private Map<String, Integer> forcedVariations;
    @Nullable
    private String url;
}