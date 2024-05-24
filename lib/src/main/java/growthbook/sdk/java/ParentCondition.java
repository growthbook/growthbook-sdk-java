package growthbook.sdk.java;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ParentCondition {
    private String id;
    private JsonObject condition;
    private Boolean gate;
}
