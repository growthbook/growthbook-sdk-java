package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.stickyBucketing.StickyAssignmentsDocument;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@Slf4j
public class UserContext {

    @Nullable
    private JsonObject attributes;

    @Nullable
    private String url;

    @Nullable
    private Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs;

    @Nullable
    private Map<String, Integer> forcedVariationsMap;

    @Nullable
    private Map<String, Object> forcedFeatureValues;

    @Nullable
    private String attributesJson;

    public JsonObject getAttributes() {
        if (this.attributes == null) {
            return new JsonObject();
        }

        return this.attributes;
    }

    public Map<String, Integer> getForcedVariationsMap() {
        return this.forcedVariationsMap == null ? new HashMap<>() : this.forcedVariationsMap;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
        this.attributes = TransformationUtil.transformAttributes(attributesJson);
    }
}
