package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.util.ForcedVariationsUtils;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
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

    /**
     * Per-user tracking callback. When set, it takes precedence over the client-level
     * tracking callback for experiments evaluated with this context. Set through
     * {@code UserScopedGrowthBook#setTrackingCallback}.
     */
    @Nullable
    private TrackingCallbackWithUser trackingCallback;

    private UserContext(UserContextBuilder userContextBuilder) {
        attributes = userContextBuilder.attributes == null ? new JsonObject() : userContextBuilder.attributes;
        url = userContextBuilder.url;
        stickyBucketAssignmentDocs = userContextBuilder.stickyBucketAssignmentDocs;
        forcedVariationsMap = ForcedVariationsUtils.normalize(userContextBuilder.forcedVariationsMap);
        forcedFeatureValues = userContextBuilder.forcedFeatureValues;
        attributesJson = userContextBuilder.attributesJson;
        trackingCallback = userContextBuilder.trackingCallback;
    }

    public static UserContextBuilder builder() {
        return new UserContextBuilder();
    }

    public UserContext witAttributesJson(String attributesJson) {
        // Build a new context using only the provided attributesJson for attributes
        return new UserContextBuilder()
                .attributesJson(attributesJson)
                .forcedVariationsMap(this.forcedVariationsMap)
                .forcedFeatureValues(this.forcedFeatureValues)
                .url(this.url)
                .stickyBucketAssignmentDocs(this.stickyBucketAssignmentDocs)
                .trackingCallback(this.trackingCallback)
                .build();
    }

    public UserContext withAttributes(JsonObject attributes) {
        return new UserContextBuilder()
                .attributes(attributes == null ? new JsonObject() : attributes)
                .forcedVariationsMap(this.forcedVariationsMap)
                .forcedFeatureValues(this.forcedFeatureValues)
                .url(this.url)
                .stickyBucketAssignmentDocs(this.stickyBucketAssignmentDocs)
                .trackingCallback(this.trackingCallback)
                .build();
    }

    /**
     * Returns a builder pre-populated with this context's fields, for producing a
     * modified copy. Used by {@code UserScopedGrowthBook} to apply per-user state
     * changes without mutating the shared context in place.
     *
     * @return a builder seeded with the current field values
     */
    public UserContextBuilder toBuilder() {
        return new UserContextBuilder()
                .attributes(this.attributes)
                .attributesJson(this.attributesJson)
                .url(this.url)
                .stickyBucketAssignmentDocs(this.stickyBucketAssignmentDocs)
                .forcedVariationsMap(this.forcedVariationsMap)
                .forcedFeatureValues(this.forcedFeatureValues)
                .trackingCallback(this.trackingCallback);
    }

    public JsonObject getAttributes() {
        return this.attributes;
    }

    public Map<String, Integer> getForcedVariationsMap() {
        return this.forcedVariationsMap;
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

    @Nullable
    public TrackingCallbackWithUser getTrackingCallback() {
        return trackingCallback;
    }

    public static class UserContextBuilder {
        @Nullable
        private JsonObject attributes;

        @Nullable
        private String url;

        @Nullable
        private Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs;

        @Nullable
        private Map<String, ?> forcedVariationsMap;

        @Nullable
        private Map<String, Object> forcedFeatureValues;

        @Nullable
        private String attributesJson;

        @Nullable
        private TrackingCallbackWithUser trackingCallback;

        public UserContextBuilder trackingCallback(@Nullable TrackingCallbackWithUser trackingCallback) {
            this.trackingCallback = trackingCallback;
            return this;
        }

        public UserContextBuilder attributesJson(String attributesJson) {
            this.attributesJson = attributesJson;
            // Only transform if attributes not explicitly provided
            if (this.attributes == null) {
                this.attributes = TransformationUtil.transformAttributes(attributesJson);
            }
            return this;
        }

        public UserContextBuilder attributes(JsonObject attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * Set attributes from a plain {@link Map}, converting it into the internal
         * {@link JsonObject} using the SDK's shared Gson instance. This lets calling
         * code pass attributes without touching the Gson/JSON layer, e.g.
         * <pre>{@code
         * UserContext.builder()
         *     .attributesMap(Map.of("userId", userId, "country", country))
         *     .build();
         * }</pre>
         *
         * <p>This is a separate method from {@link #attributes(JsonObject)} rather than an
         * overload so that existing {@code attributes(null)} calls remain unambiguous.
         *
         * <p>Values should be JSON-compatible: primitives, {@link String}, {@link java.util.List},
         * nested {@link Map}, or {@code null}. Other types are serialized via Gson reflection,
         * which is lossy for types such as {@link java.util.Date} and may fail for
         * {@code java.time} types (e.g. {@link java.time.Instant}) on newer JDKs; convert those
         * to a JSON-compatible value (e.g. an ISO-8601 string) before passing them in.
         *
         * @param attributes attributes as key-value pairs, or {@code null} for none
         * @return this builder
         */
        public UserContextBuilder attributesMap(@Nullable Map<String, ?> attributes) {
            // Mirror attributes((JsonObject) null): leave attributes unset so a later
            // attributesJson(...) still applies and build() defaults to an empty object.
            this.attributes = attributes == null
                    ? null
                    : GrowthBookJsonUtils.getInstance().gson.toJsonTree(attributes).getAsJsonObject();
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

        public UserContextBuilder forcedVariationsMap(Map<String, ?> forcedVariationsMap) {
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
