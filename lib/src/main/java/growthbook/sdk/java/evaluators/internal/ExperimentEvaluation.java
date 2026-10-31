package growthbook.sdk.java.evaluators.internal;

import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.bucketing.Bucketing;
import growthbook.sdk.java.evaluators.internal.bucketing.StickyBuckets;
import growthbook.sdk.java.evaluators.internal.condition.Conditions;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.GeneratedStickyBucketAssignmentDocModel;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.model.StickyBucketVariation;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Experiment evaluation in spec order: enabled and variation count, query string override,
 * forced variation, active flag, hash attribute, sticky bucket, targeting (filters or namespace,
 * condition, prerequisites), hashing and bucketing, version block, forced index, QA mode — then
 * sticky bucket persistence and the exposure.
 *
 * <p>The experiment is only read; defaults for missing weights, coverage and ranges are computed
 * into locals.
 */
@Slf4j
final class ExperimentEvaluation {

    private final Evaluation evaluation;

    ExperimentEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }

    <T> ExperimentResult<T> run(Experiment<T> experiment, @Nullable String featureId) {
        ExperimentResult<T> decidedWithoutHashing = resultWithoutHashing(experiment, featureId);
        if (decidedWithoutHashing != null) {
            return decidedWithoutHashing;
        }

        HashAttributeAndHashValue hashAttribute = Bucketing.getHashAttribute(
                experiment.getHashAttribute(),
                isStickyBucketingEnabled(experiment) ? experiment.getFallbackAttribute() : null,
                evaluation.context().getUser().getAttributes()
        );
        String hashValue = hashAttribute.getHashValue();
        if (hashValue.isEmpty() || hashValue.equals("null")) {
            return notInExperiment(experiment, featureId);
        }

        StickyBucketVariation stickyBucket = stickyBucket(experiment);
        boolean foundStickyBucket = stickyBucket.getVariation() >= 0;
        if (!foundStickyBucket && isExcludedByTargeting(experiment, hashValue)) {
            return notInExperiment(experiment, featureId);
        }

        String seed = experiment.getSeed() != null ? experiment.getSeed() : experiment.getKey();
        Double hash = Bucketing.hash(hashValue, experiment.getHashVersion() != null ? experiment.getHashVersion() : 1, seed);
        if (hash == null) {
            return notInExperiment(experiment, featureId);
        }
        int assigned = foundStickyBucket
                ? stickyBucket.getVariation()
                : Bucketing.chooseVariation(hash, bucketRanges(experiment, variationsOf(experiment).size()));
        return assignedResult(experiment, featureId, hashAttribute, assigned, hash, stickyBucket);
    }

    /**
     * The steps decided before the user is hashed: disabled SDK or fewer than two variations,
     * query string override, forced variation and inactive experiment.
     *
     * @return the result, or {@code null} to continue with hashing
     */
    @Nullable
    private <T> ExperimentResult<T> resultWithoutHashing(Experiment<T> experiment, @Nullable String featureId) {
        EvaluationContext context = evaluation.context();
        int numberOfVariations = variationsOf(experiment).size();
        if (!Boolean.TRUE.equals(context.getOptions().getEnabled()) || numberOfVariations < 2) {
            return notInExperiment(experiment, featureId);
        }
        Integer override = ForcedValues.getQueryStringOverride(experiment.getKey(), context.getOptions().getUrl(), numberOfVariations);
        if (override != null) {
            return result(experiment, override, false, featureId, null, false);
        }
        Integer forcedVariation = forcedVariation(experiment.getKey());
        if (forcedVariation != null) {
            return result(experiment, forcedVariation, false, featureId, null, false);
        }
        if (Boolean.FALSE.equals(experiment.getIsActive())) {
            return notInExperiment(experiment, featureId);
        }
        return null;
    }

    private <T> StickyBucketVariation stickyBucket(Experiment<T> experiment) {
        if (!isStickyBucketingEnabled(experiment)) {
            return new StickyBucketVariation(-1, null);
        }
        return StickyBuckets.getStickyBucketVariation(
                evaluation.context(),
                experiment.getKey(),
                experiment.getHashAttribute(),
                experiment.getFallbackAttribute(),
                experiment.getBucketVersion(),
                experiment.getMinBucketVersion(),
                experiment.getMeta()
        );
    }

    /**
     * The steps after bucketing: version block, no variation, forced index and QA mode; then the
     * user is in the experiment, the sticky bucket is persisted and the exposure reported.
     */
    private <T> ExperimentResult<T> assignedResult(
            Experiment<T> experiment,
            @Nullable String featureId,
            HashAttributeAndHashValue hashAttribute,
            int assigned,
            double hash,
            StickyBucketVariation stickyBucket
    ) {
        if (Boolean.TRUE.equals(stickyBucket.getVersionIsBlocked())) {
            return result(experiment, -1, false, featureId, null, true);
        }
        if (assigned < 0) {
            return notInExperiment(experiment, featureId);
        }
        if (experiment.getForce() != null) {
            return result(experiment, experiment.getForce(), false, featureId, null, false);
        }
        if (Boolean.TRUE.equals(evaluation.context().getOptions().getIsQaMode())) {
            return notInExperiment(experiment, featureId);
        }

        ExperimentResult<T> result = result(experiment, assigned, true, featureId, hash, stickyBucket.getVariation() >= 0);
        if (isStickyBucketingEnabled(experiment)) {
            persistStickyBucket(experiment, hashAttribute, result);
        }
        evaluation.events().experimentViewed(experiment, result);
        return result;
    }

    /**
     * Filters (or the legacy namespace when there are no filters), the targeting condition and
     * the prerequisites, in that order.
     */
    private <T> boolean isExcludedByTargeting(Experiment<T> experiment, String hashValue) {
        return isOutsideAudience(experiment, hashValue)
                || failsCondition(experiment.getConditionJson())
                || failsPrerequisites(experiment.getParentConditions());
    }

    /**
     * Filters, or the legacy namespace when there are none. Namespaces are deprecated in the
     * model but still part of the spec, so they are read here and nowhere else.
     */
    @SuppressWarnings("deprecation")
    private <T> boolean isOutsideAudience(Experiment<T> experiment, String hashValue) {
        if (experiment.getFilters() != null) {
            return Bucketing.isFilteredOut(experiment.getFilters(), evaluation.context().getUser().getAttributes());
        }
        return experiment.getNamespace() != null && !Bucketing.inNamespace(
                hashValue,
                experiment.getNamespace().getId(),
                experiment.getNamespace().getRangeStart(),
                experiment.getNamespace().getRangeEnd());
    }

    private boolean failsCondition(@Nullable JsonObject condition) {
        EvaluationContext context = evaluation.context();
        return condition != null
                && !Conditions.matches(context.getUser().getAttributes(), condition, context.getGlobal().getSavedGroups());
    }

    private boolean failsPrerequisites(@Nullable List<ParentCondition> parentConditions) {
        if (parentConditions == null) {
            return false;
        }
        Set<String> chain = evaluation.stack().chain();
        for (ParentCondition parentCondition : parentConditions) {
            Prerequisites.Outcome outcome = evaluation.prerequisites().check(parentCondition, chain, null);
            if (outcome == Prerequisites.Outcome.NOT_MET) {
                log.debug("Experiment blocked by prerequisite {}", parentCondition.getId());
            }
            if (outcome != Prerequisites.Outcome.MET) {
                return true;
            }
        }
        return false;
    }

    private <T> List<BucketRange> bucketRanges(Experiment<T> experiment, int numberOfVariations) {
        if (experiment.getRanges() != null) {
            return experiment.getRanges();
        }
        List<Double> weights = experiment.getWeights() != null
                ? experiment.getWeights()
                : Bucketing.getEqualWeights(numberOfVariations);
        double coverage = experiment.getCoverage() != null ? experiment.getCoverage() : 1.0;
        return Bucketing.getBucketRanges(numberOfVariations, coverage, weights);
    }

    /**
     * Records the assignment in the user's documents, so later evaluations for this user see it,
     * and saves the changed document. A failing {@link StickyBucketService} is logged and the
     * assignment is kept for this user context only.
     */
    private <T> void persistStickyBucket(Experiment<T> experiment, HashAttributeAndHashValue hashAttribute, ExperimentResult<T> result) {
        EvaluationContext context = evaluation.context();
        Map<String, String> assignments = Collections.singletonMap(
                StickyBuckets.getStickyBucketExperimentKey(experiment.getKey(), experiment.getBucketVersion()),
                result.getKey()
        );

        GeneratedStickyBucketAssignmentDocModel docModel = StickyBuckets.generateStickyBucketAssignmentDoc(
                context.getUser().getStickyBucketAssignmentDocs(),
                hashAttribute.getHashAttribute(),
                hashAttribute.getHashValue(),
                assignments);

        if (!docModel.isChanged()) {
            return;
        }

        if (context.getUser().getStickyBucketAssignmentDocs() == null) {
            context.getUser().setStickyBucketAssignmentDocs(new HashMap<>());
        }
        context.getUser().getStickyBucketAssignmentDocs().put(docModel.getKey(), docModel.getStickyAssignmentsDocument());

        StickyBucketService stickyBucketService = context.getOptions().getStickyBucketService();
        if (stickyBucketService != null) {
            try {
                stickyBucketService.saveAssignments(docModel.getStickyAssignmentsDocument());
            } catch (RuntimeException e) {
                log.warn("Sticky bucket service failed to save assignments; continuing without persisting them.", e);
            }
        }
    }

    private <T> ExperimentResult<T> notInExperiment(Experiment<T> experiment, @Nullable String featureId) {
        return result(experiment, -1, false, featureId, null, false);
    }

    /**
     * Builds the result for a variation index. An index outside the variations falls back to the
     * baseline (variation 0) with {@code inExperiment = false}, while {@code variationId} keeps
     * the index as given.
     */
    private <T> ExperimentResult<T> result(
            Experiment<T> experiment,
            int variationIndex,
            boolean hashUsed,
            @Nullable String featureId,
            @Nullable Double hashBucket,
            boolean stickyBucketUsed
    ) {
        List<T> variations = variationsOf(experiment);
        boolean inExperiment = variationIndex >= 0 && variationIndex < variations.size();
        int targetVariationIndex = inExperiment ? variationIndex : 0;

        HashAttributeAndHashValue hashAttribute = Bucketing.getHashAttribute(
                experiment.getHashAttribute(),
                isStickyBucketingEnabled(experiment) ? experiment.getFallbackAttribute() : null,
                evaluation.context().getUser().getAttributes());

        List<VariationMeta> experimentMeta = experiment.getMeta() != null ? experiment.getMeta() : Collections.emptyList();
        VariationMeta meta = experimentMeta.size() > targetVariationIndex ? experimentMeta.get(targetVariationIndex) : null;

        return ExperimentResult
                .<T>builder()
                .inExperiment(inExperiment)
                .variationId(variationIndex)
                .value(variations.size() > targetVariationIndex ? variations.get(targetVariationIndex) : null)
                .hashAttribute(hashAttribute.getHashAttribute())
                .hashValue(hashAttribute.getHashValue())
                .key(meta != null ? meta.getKey() : targetVariationIndex + "")
                .featureId(featureId)
                .hashUsed(hashUsed)
                .stickyBucketUsed(stickyBucketUsed)
                .name(meta != null ? meta.getName() : null)
                .bucket(hashBucket)
                .passThrough(meta != null ? meta.getPassThrough() : null)
                .build();
    }

    private <T> boolean isStickyBucketingEnabled(Experiment<T> experiment) {
        return evaluation.context().getOptions().getStickyBucketService() != null
                && !Boolean.TRUE.equals(experiment.getDisableStickyBucketing());
    }

    @Nullable
    private Integer forcedVariation(String key) {
        EvaluationContext context = evaluation.context();
        Map<String, Integer> userForcedVariations = context.getUser() != null
                ? context.getUser().getForcedVariationsMap()
                : null;
        if (userForcedVariations != null && userForcedVariations.containsKey(key)) {
            return userForcedVariations.get(key);
        }

        Map<String, Integer> globalForcedVariations = context.getGlobal() != null
                ? context.getGlobal().getForcedVariations()
                : null;
        if (globalForcedVariations != null && globalForcedVariations.containsKey(key)) {
            return globalForcedVariations.get(key);
        }

        return null;
    }

    private static <T> List<T> variationsOf(Experiment<T> experiment) {
        return experiment.getVariations() != null ? experiment.getVariations() : Collections.emptyList();
    }
}
