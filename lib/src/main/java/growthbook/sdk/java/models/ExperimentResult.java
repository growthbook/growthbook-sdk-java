package growthbook.sdk.java.models;

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
    Boolean hashUsed;

    @Nullable
    String featureId;
}
