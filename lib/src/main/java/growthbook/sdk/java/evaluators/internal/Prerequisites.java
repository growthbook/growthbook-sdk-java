package growthbook.sdk.java.evaluators.internal;

import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.Conditions;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Checks one prerequisite: evaluates the parent feature within the same evaluation and matches
 * its value against the parent condition. What a failed check means — skip the rule, block the
 * feature, or exclude from the experiment — is up to the caller.
 */
final class Prerequisites {

    enum Outcome {
        MET,
        NOT_MET,
        CYCLIC
    }

    private final Evaluation evaluation;

    Prerequisites(Evaluation evaluation) {
        this.evaluation = evaluation;
    }

    /**
     * Evaluates the parent feature and matches its value against the parent condition.
     *
     * @param parentCondition   the prerequisite
     * @param evaluatedFeatures the prerequisite chain as it was before this rule or experiment, so
     *                          sibling prerequisites do not see each other as cycles
     * @param valueTypeClass    the type the parent feature is evaluated with
     * @return {@link Outcome#CYCLIC} when the parent depends on this feature, otherwise whether
     * the condition is met
     */
    Outcome check(ParentCondition parentCondition, Set<String> evaluatedFeatures, @Nullable Class<?> valueTypeClass) {
        evaluation.stack().restoreChain(evaluatedFeatures);
        FeatureResult<Object> parentResult = evaluation.features().evaluate(parentCondition.getId(), valueTypeClass);

        if (FeatureResultSource.CYCLIC_PREREQUISITE.equals(parentResult.getSource())) {
            return Outcome.CYCLIC;
        }

        JsonObject parentAttributes = new JsonObject();
        if (parentResult.getValue() != null) {
            parentAttributes.add("value", GrowthBookJsonUtils.getInstance().gson.toJsonTree(parentResult.getValue()));
        }

        boolean met = Conditions.matches(
                parentAttributes,
                parentCondition.getCondition(),
                evaluation.context().getGlobal().getSavedGroups()
        );
        return met ? Outcome.MET : Outcome.NOT_MET;
    }
}
