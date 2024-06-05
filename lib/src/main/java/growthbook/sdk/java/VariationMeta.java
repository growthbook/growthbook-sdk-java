package growthbook.sdk.java;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;


/**
 * Meta info about an experiment variation
 */
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class VariationMeta {
    /**
     * A unique key for this variation
     */
    @Nullable
    String key;

    /**
     * A human-readable name for this variation
     */
    @Nullable
    String name;

    /**
     * Used to implement holdout groups
     */
    @SerializedName("passthrough")
    @Nullable
    Boolean passThrough;
}
