package growthbook.sdk.java;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignedExperiment{
    private String experimentKey;
    private Boolean inExperiment;
    private Integer variationId;
}
