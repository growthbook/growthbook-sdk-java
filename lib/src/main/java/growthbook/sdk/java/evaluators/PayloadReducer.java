package growthbook.sdk.java.evaluators;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.evaluators.internal.condition.CondPruner;
import growthbook.sdk.java.evaluators.internal.payload.PayloadReduction;
import growthbook.sdk.java.model.ReducePayloadOptions;

import javax.annotation.Nullable;

/**
 * Shrinks a features payload for a user whose attributes are only partly known, e.g. on a server
 * bootstrapping a client SDK. The reduced payload evaluates the same as the full one for anyone
 * who agrees with the known attributes.
 *
 * <p>Stateless and safe for concurrent use. Inputs are never modified.</p>
 */
public final class PayloadReducer {

    private PayloadReducer() {
    }

    /**
     * Partially evaluates a condition when only some attributes are known. Attributes missing from
     * {@code attributes} are unknown, and so is any nested (dotted) path.
     *
     * @param attributes  the known attributes
     * @param condition   the condition to prune
     * @param savedGroups the payload's saved groups, or {@code null} for none
     * @return a boolean {@link JsonPrimitive} once the result can't depend on unknown attributes,
     * otherwise a {@link JsonObject} with the condition minus every branch that is already decided
     */
    public static JsonElement pruneCondition(@Nullable JsonObject attributes, JsonObject condition, @Nullable JsonObject savedGroups) {
        return CondPruner.pruneCondition(attributes != null ? attributes : new JsonObject(), condition, savedGroups);
    }

    /**
     * Reduces a decrypted features payload for one user.
     *
     * <p>Features and auto experiments are kept or dropped by the {@code include}/{@code exclude}
     * filters on their project, tag and custom-field metadata. Rule and experiment conditions are
     * reduced with {@link #pruneCondition}: a condition that always passes is removed, and one that
     * never passes becomes {@code {"$not": {}}} (a force rule's value becomes {@code null}). The
     * prerequisites of kept features stay, unless {@link ReducePayloadOptions.Prerequisites#FILTER}
     * is set. Saved groups and contextual bandits nothing references any more are dropped.
     *
     * @param payload    the decrypted payload ({@code features}, {@code experiments},
     *                   {@code savedGroups}, {@code contextualBandits}, ...)
     * @param attributes the known attributes, global and per-user merged; {@code null} for none
     * @param options    filters and prerequisite handling; {@code null} for defaults
     * @return a new payload: the input's other keys, with {@code features}, {@code experiments} and
     * {@code savedGroups} reduced, and {@code contextualBandits} reduced when the input had them
     */
    public static JsonObject reducePayload(JsonObject payload, @Nullable JsonObject attributes, @Nullable ReducePayloadOptions options) {
        return PayloadReduction.reduce(payload,
                attributes != null ? attributes : new JsonObject(),
                options != null ? options : new ReducePayloadOptions());
    }
}
