package growthbook.sdk.java;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;


/**
 * Meta info about an experiment variation
 */
@Data
@Builder
@AllArgsConstructor
public class VariationMeta {
    @Nullable String key;
    @Nullable String name;

    @SerializedName("passthrough")
    @Nullable Boolean passThrough;
}
