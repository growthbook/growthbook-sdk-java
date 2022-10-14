package growthbook.sdk.java.models;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class TrackingResult<ValueType> {
    ValueType value;

    Integer variationId;

    Boolean inExperiment;
}
