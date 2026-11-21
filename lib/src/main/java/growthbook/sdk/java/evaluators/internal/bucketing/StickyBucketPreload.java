package growthbook.sdk.java.evaluators.internal.bucketing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRule;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads a user's sticky bucket documents before evaluation, for the single-user
 * {@link growthbook.sdk.java.GrowthBook}: one document per identifier attribute the features
 * bucket by. A failing {@link StickyBucketService} is logged and treated as having no documents;
 * {@link #loadStickyBuckets} reports the failure so the caller can stop saving assignments.
 */
@Slf4j
public final class StickyBucketPreload {

    private static final String FEATURES_KEY = "features";
    private static final String DEFAULT_HASH_ATTRIBUTE = "id";

    private StickyBucketPreload() {
    }

    /**
     * Loads the assignment documents for every identifier attribute the features bucket by, and
     * stores them in the context.
     *
     * @param context            the context with the sticky bucket service; skipped without one
     * @param featuresDataModel  a features payload whose features replace the context's, or {@code null}
     * @param attributeOverrides the user attributes
     */
    public static void refreshStickyBuckets(GBContext context,
                                            @Nullable String featuresDataModel,
                                            JsonObject attributeOverrides) {
        loadStickyBuckets(context, featuresDataModel, attributeOverrides);
    }

    /**
     * {@link #refreshStickyBuckets} that reports whether the stored assignments were read.
     *
     * @param context            the context with the sticky bucket service; skipped without one
     * @param featuresDataModel  a features payload whose features replace the context's, or {@code null}
     * @param attributeOverrides the user attributes
     * @return {@code false} when the service failed to load assignments. The context then holds no
     * documents and evaluation buckets normally; the caller must not save those assignments, because
     * they could replace the variation the store already holds. {@code true} otherwise, including
     * when no service is configured.
     */
    public static boolean loadStickyBuckets(GBContext context,
                                            @Nullable String featuresDataModel,
                                            JsonObject attributeOverrides) {
        StickyBucketService stickyBucketService = context.getStickyBucketService();
        if (stickyBucketService == null) {
            log.debug("refreshStickyBuckets: sticky bucket service not configured, skipping refresh.");
            return true;
        }
        Map<String, String> stickyBucketAttributes = getStickyBucketAttributes(context, featuresDataModel, attributeOverrides);

        try {
            context.setStickyBucketAssignmentDocs(stickyBucketService.getAllAssignments(stickyBucketAttributes));
            return true;
        } catch (RuntimeException e) {
            log.warn("Sticky bucket service failed to load assignments; continuing with normal bucketing"
                    + " and not saving assignments until they load.", e);
            context.setStickyBucketAssignmentDocs(new HashMap<>());
            return false;
        }
    }

    /**
     * @param context            the context whose features are used when the payload has none
     * @param featuresDataModel  a features payload, or {@code null}
     * @param attributeOverrides the user attributes
     * @return the hash value of every identifier attribute the features bucket by
     */
    public static Map<String, String> getStickyBucketAttributes(GBContext context,
                                                                @Nullable String featuresDataModel,
                                                                JsonObject attributeOverrides) {
        Map<String, String> attributes = new HashMap<>();
        for (String attr : identifierAttributes(featuresOf(context, featuresDataModel))) {
            attributes.put(attr, Bucketing.getHashAttribute(attr, null, attributeOverrides).getHashValue());
        }
        return attributes;
    }

    private static Collection<Feature<?>> featuresOf(GBContext context, @Nullable String featuresDataModel) {
        Map<String, Feature<?>> payloadFeatures = parseFeatures(featuresDataModel);
        if (!payloadFeatures.isEmpty()) {
            return payloadFeatures.values();
        }
        return context.getFeatures() != null ? context.getFeatures().values() : Collections.emptyList();
    }

    private static Map<String, Feature<?>> parseFeatures(@Nullable String featuresDataModel) {
        if (featuresDataModel == null || featuresDataModel.isEmpty()) {
            return Collections.emptyMap();
        }
        JsonObject payload = GrowthBookJsonUtils.getInstance().gson.fromJson(featuresDataModel, JsonObject.class);
        JsonElement features = payload != null ? payload.get(FEATURES_KEY) : null;
        if (features == null) {
            return Collections.emptyMap();
        }
        return TransformationUtil.transformFeatures(features.toString().trim());
    }

    /**
     * The hash and fallback attributes of every experiment rule.
     */
    private static Set<String> identifierAttributes(Collection<Feature<?>> features) {
        Set<String> attributes = new HashSet<>();
        for (Feature<?> feature : features) {
            if (feature != null && feature.getRules() != null) {
                for (FeatureRule<?> rule : feature.getRules()) {
                    addIdentifierAttributes(rule, attributes);
                }
            }
        }
        return attributes;
    }

    private static void addIdentifierAttributes(FeatureRule<?> rule, Set<String> attributes) {
        if (rule.getVariations() == null || rule.getVariations().isEmpty()) {
            return;
        }
        attributes.add(rule.getHashAttribute() != null ? rule.getHashAttribute() : DEFAULT_HASH_ATTRIBUTE);
        if (rule.getFallbackAttribute() != null) {
            attributes.add(rule.getFallbackAttribute());
        }
    }

}
