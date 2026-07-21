package growthbook.sdk.java.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.internal.GlobalContextManager;
import growthbook.sdk.java.multiusermode.internal.RemoteEvalCoordinator;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the per-request {@link UserContext} shared by the local and remote evaluation paths:
 * overlays user attributes on the client's global attributes and, when a
 * {@link StickyBucketService} is configured, preloads the user's sticky-bucket assignment
 * documents.
 *
 * <p>The sticky preload preserves the assignment behaviour that {@code GrowthBookClient} relied on
 * before evaluation was split across {@link GlobalContextManager} and {@link RemoteEvalCoordinator}.
 * Without it, multi-user clients using a {@link StickyBucketService} would evaluate experiments
 * without their persisted assignments, changing assignment behaviour and diverging from the
 * sticky-bucketing contract shared across GrowthBook SDKs.
 */
@UtilityClass
public final class UserContextUtils {

    /**
     * Merges global and per-user attributes and preloads sticky-bucket assignments for the merged
     * attributes when a {@link StickyBucketService} is configured and the caller has not already
     * supplied assignment documents.
     *
     * @param options     client options (global attributes and optional sticky bucket service)
     * @param userContext the per-request user context; {@code null} is treated as an empty context
     * @return a new user context carrying the merged attributes and any preloaded sticky docs
     */
    public static UserContext mergeAttributesAndPreloadSticky(Options options, @Nullable UserContext userContext) {
        UserContext safeUserContext = userContext == null ? UserContext.builder().build() : userContext;
        JsonObject mergedAttributes = mergeAttributes(options, safeUserContext);
        UserContext mergedUserContext = safeUserContext.withAttributes(mergedAttributes);
        preloadStickyBucketAssignments(options, mergedUserContext, mergedAttributes);
        return mergedUserContext;
    }

    private static JsonObject mergeAttributes(Options options, UserContext userContext) {
        JsonObject merged = globalAttributes(options);
        JsonObject userAttributes = userContext.getAttributes();
        if (userAttributes != null) {
            for (Map.Entry<String, JsonElement> entry : userAttributes.entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private static JsonObject globalAttributes(Options options) {
        if (options.getGlobalAttributes() == null) {
            return new JsonObject();
        }
        JsonObject globalAttributes = GrowthBookJsonUtils.getInstance()
                .gson
                .fromJson(options.getGlobalAttributes(), JsonObject.class);
        return globalAttributes == null ? new JsonObject() : globalAttributes;
    }

    /**
     * Fetches sticky-bucket assignment docs for the user's (string-valued) attributes once per
     * request, unless the caller preloaded them. Mirrors the previous {@code GrowthBookClient}
     * behaviour so a configured {@link StickyBucketService} keeps driving experiment assignments.
     */
    private static void preloadStickyBucketAssignments(Options options, UserContext userContext, JsonObject attributes) {
        StickyBucketService stickyBucketService = options.getStickyBucketService();
        if (stickyBucketService == null || userContext.getStickyBucketAssignmentDocs() != null) {
            return;
        }
        Map<String, String> attributeStrings = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                attributeStrings.put(entry.getKey(), value.getAsString());
            }
        }
        Map<String, StickyAssignmentsDocument> docs = stickyBucketService.getAllAssignments(attributeStrings);
        userContext.setStickyBucketAssignmentDocs(docs);
    }
}
