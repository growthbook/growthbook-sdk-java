package growthbook.sdk.java.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;

/**
 * Options for reducing a features payload, e.g. on a server bootstrapping a client SDK.
 *
 * @see growthbook.sdk.java.evaluators.PayloadReducer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReducePayloadOptions {
    /**
     * Keep only the features and auto experiments that match every filter.
     */
    @Nullable
    private PayloadFilters include;

    /**
     * Remove the features and auto experiments that match any filter.
     */
    @Nullable
    private PayloadFilters exclude;

    /**
     * How the prerequisites of what is kept are treated; {@code null} means {@link Prerequisites#KEEP}.
     */
    @Nullable
    private Prerequisites prerequisites;

    /**
     * How {@link ReducePayloadOptions} treats prerequisite features.
     */
    public enum Prerequisites {
        /**
         * Send the prerequisites of what is kept even when the filters remove them, so the
         * client evaluates the same as with the full payload.
         */
        @SerializedName("keep") KEEP,

        /**
         * Apply the filters to prerequisites too. Holdouts are always kept.
         */
        @SerializedName("filter") FILTER
    }
}
