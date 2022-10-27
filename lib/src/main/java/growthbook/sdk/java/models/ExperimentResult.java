package growthbook.sdk.java.models;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder
@AllArgsConstructor
public class ExperimentResult<ValueType> {
    @Nullable
    ValueType value;

    @Nullable
    Integer variationId;

    @Builder.Default
    Boolean inExperiment = false;

    @Nullable
    @Builder.Default
    String hashAttribute = "id";

    @Nullable
    String hashValue;

    @Nullable
    String featureId;

    @Builder.Default
    Boolean hashUsed = false;
}
