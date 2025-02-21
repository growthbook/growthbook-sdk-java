package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UserContext {

    @Nullable
    private JsonObject attributes;

    @Nullable
    private String url;

    @Nullable
    @Setter // we need this setter for evaluating with stickybucketing
    private Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs;

    @Nullable
    private Map<String, Integer> forcedVariationsMap;

    @Nullable
    private Map<String, Object> forcedFeatureValues;

    @Nullable
    private String attributesJson;

    private UserContext(UserContextBuilder userContextBuilder) {
        attributes = userContextBuilder.attributes;
        url = userContextBuilder.url;
        stickyBucketAssignmentDocs = userContextBuilder.stickyBucketAssignmentDocs;
        forcedVariationsMap = userContextBuilder.forcedVariationsMap;
        forcedFeatureValues = userContextBuilder.forcedFeatureValues;
        attributesJson = userContextBuilder.attributesJson;
    }

    public UserContext witAttributesJson(String attributesJson) {
        return new UserContextBuilder()
                .attributesJson(attributesJson)
                .attributes(this.attributes)
                .forcedVariationsMap(this.forcedVariationsMap)
                .forcedFeatureValues(this.forcedFeatureValues)
                .url(this.url)
                .stickyBucketAssignmentDocs(this.stickyBucketAssignmentDocs)
                .build();
    }

    public JsonObject getAttributes() {
        if (this.attributes == null) {
            return new JsonObject();
        }

        return this.attributes;
    }

    public Map<String, Integer> getForcedVariationsMap() {
        return this.forcedVariationsMap == null ? new HashMap<>() : this.forcedVariationsMap;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public Map<String, StickyAssignmentsDocument> getStickyBucketAssignmentDocs() {
        return stickyBucketAssignmentDocs;
    }

    @Nullable
    public Map<String, Object> getForcedFeatureValues() {
        return forcedFeatureValues;
    }

    @Nullable
    public String getAttributesJson() {
        return attributesJson;
    }

    public static class UserContextBuilder {
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

        public UserContextBuilder attributesJson(String attributesJson) {
            this.attributesJson = attributesJson;
            this.attributes = TransformationUtil.transformAttributes(attributesJson);
            return this;
        }

        public UserContextBuilder attributes(JsonObject attributes) {
            this.attributes = attributes;
            return this;
        }

        public UserContextBuilder url(String url) {
            this.url = url;
            return this;
        }

        public UserContextBuilder stickyBucketAssignmentDocs(Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs) {
            this.stickyBucketAssignmentDocs = stickyBucketAssignmentDocs;
            return this;
        }

        public UserContextBuilder forcedVariationsMap(Map<String, Integer> forcedVariationsMap) {
            this.forcedVariationsMap = forcedVariationsMap;
            return this;
        }

        public UserContextBuilder forcedFeatureValues(Map<String, Object> forcedFeatureValues) {
            this.forcedFeatureValues = forcedFeatureValues;
            return this;
        }

        public UserContext build() {
            return new UserContext(this);
        }
    }
}
