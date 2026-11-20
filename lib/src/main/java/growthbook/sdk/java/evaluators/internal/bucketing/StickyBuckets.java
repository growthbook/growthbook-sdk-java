package growthbook.sdk.java.evaluators.internal.bucketing;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.GeneratedStickyBucketAssignmentDocModel;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.StickyBucketVariation;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sticky bucketing during evaluation: resolving a stored variation and building updated
 * documents. Stateless; every method reads and writes only the documents it is given.
 */
public final class StickyBuckets {

    private static final String KEY_SEPARATOR = "||";

    private StickyBuckets() {
    }

    /**
     * Merges the user's assignments from the document of the experiment's hash attribute and, if
     * present, of its fallback attribute; the hash attribute's assignments win.
     *
     * @param context              the evaluation context with the user's documents
     * @param expHashAttribute     the experiment's hash attribute, {@code id} when {@code null}
     * @param expFallbackAttribute the experiment's fallback attribute, or {@code null}
     * @return the assignments by experiment key; empty when the user has no documents
     */
    public static Map<String, String> getStickyBucketAssignments(
            EvaluationContext context,
            @Nullable String expHashAttribute,
            @Nullable String expFallbackAttribute
    ) {
        Map<String, String> mergedAssignments = new HashMap<>();
        Map<String, StickyAssignmentsDocument> documents = context.getUser().getStickyBucketAssignmentDocs();
        if (documents == null) {
            return mergedAssignments;
        }

        JsonObject attributes = context.getUser().getAttributes();
        HashAttributeAndHashValue fallback = Bucketing.getHashAttribute(expFallbackAttribute, null, attributes);
        if (!fallback.getHashValue().isEmpty()) {
            putAssignments(documents.get(documentKey(fallback)), mergedAssignments);
        }
        putAssignments(documents.get(documentKey(Bucketing.getHashAttribute(expHashAttribute, null, attributes))), mergedAssignments);
        return mergedAssignments;
    }

    private static void putAssignments(@Nullable StickyAssignmentsDocument document, Map<String, String> target) {
        if (document != null) {
            target.putAll(document.getAssignments());
        }
    }

    private static String documentKey(HashAttributeAndHashValue hashAttribute) {
        return hashAttribute.getHashAttribute() + KEY_SEPARATOR + hashAttribute.getHashValue();
    }

    /**
     * Resolves the user's sticky variation for an experiment. Users with an assignment for any
     * bucket version below {@code minExperimentBucketVersion} are blocked from the experiment.
     *
     * @param context                     the evaluation context with the user's documents
     * @param experimentKey               the experiment key
     * @param experimentHashAttribute     the hash attribute, or {@code null}
     * @param experimentFallbackAttribute the fallback attribute, or {@code null}
     * @param experimentBucketVersion     the bucket version, 0 when {@code null}
     * @param minExperimentBucketVersion  the lowest allowed bucket version, 0 when {@code null}
     * @param meta                        the variation metadata used to map a key to an index, or {@code null}
     * @return the variation index ({@code -1} when none), and whether the user is blocked
     */
    public static StickyBucketVariation getStickyBucketVariation(
            EvaluationContext context,
            String experimentKey,
            @Nullable String experimentHashAttribute,
            @Nullable String experimentFallbackAttribute,
            @Nullable Integer experimentBucketVersion,
            @Nullable Integer minExperimentBucketVersion,
            @Nullable List<VariationMeta> meta
    ) {
        int minBucketVersion = minExperimentBucketVersion != null ? minExperimentBucketVersion : 0;
        Map<String, String> assignments = getStickyBucketAssignments(context, experimentHashAttribute, experimentFallbackAttribute);

        for (int version = 0; version < minBucketVersion; version++) {
            if (assignments.containsKey(getStickyBucketExperimentKey(experimentKey, version))) {
                return new StickyBucketVariation(-1, true);
            }
        }

        String variationKey = assignments.get(getStickyBucketExperimentKey(experimentKey, experimentBucketVersion));
        if (variationKey == null) {
            return new StickyBucketVariation(-1, null);
        }
        return new StickyBucketVariation(variationIndex(meta, variationKey), null);
    }

    /**
     * @param experimentKey           the experiment key
     * @param experimentBucketVersion the bucket version, 0 when {@code null}
     * @return the key an assignment is stored under, e.g. {@code my-experiment__0}
     */
    public static String getStickyBucketExperimentKey(String experimentKey, @Nullable Integer experimentBucketVersion) {
        return experimentKey + "__" + (experimentBucketVersion != null ? experimentBucketVersion : 0);
    }

    /**
     * Builds the user's document with {@code assignments} merged in. A document with no new
     * assignments is reused as is and reported unchanged, so it is not saved again.
     *
     * @param stickyBucketAssignmentDocs the user's documents, or {@code null}
     * @param attributeName              the hash attribute the document belongs to
     * @param attributeValue             its value
     * @param assignments                the assignments to merge in
     * @return the document, its key and whether it changed
     */
    public static GeneratedStickyBucketAssignmentDocModel generateStickyBucketAssignmentDoc(
            @Nullable Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs,
            String attributeName,
            String attributeValue,
            Map<String, String> assignments
    ) {
        String key = attributeName + KEY_SEPARATOR + attributeValue;
        StickyAssignmentsDocument existing = stickyBucketAssignmentDocs != null ? stickyBucketAssignmentDocs.get(key) : null;
        Map<String, String> existingAssignments = existing != null ? existing.getAssignments() : new HashMap<>();

        if (!changesAny(existingAssignments, assignments)) {
            StickyAssignmentsDocument doc = new StickyAssignmentsDocument(attributeName, attributeValue, existingAssignments);
            return new GeneratedStickyBucketAssignmentDocModel(key, doc, false);
        }

        Map<String, String> newAssignments = new HashMap<>(existingAssignments);
        newAssignments.putAll(assignments);
        StickyAssignmentsDocument doc = new StickyAssignmentsDocument(attributeName, attributeValue, newAssignments);
        return new GeneratedStickyBucketAssignmentDocModel(key, doc, true);
    }

    private static boolean changesAny(Map<String, String> existing, Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (!Objects.equals(entry.getValue(), existing.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private static int variationIndex(@Nullable List<VariationMeta> meta, String variationKey) {
        if (meta == null) {
            return -1;
        }
        for (int i = 0; i < meta.size(); i++) {
            if (variationKey.equals(meta.get(i).getKey())) {
                return i;
            }
        }
        return -1;
    }
}
