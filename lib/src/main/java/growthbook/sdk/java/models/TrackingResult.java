package growthbook.sdk.java.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TrackingResult<ValueType> {
    ValueType value;

    Integer variationId;

    Boolean inExperiment;
}
