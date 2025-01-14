package growthbook.sdk.java;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignedExperiment<ValueType> {
    private Experiment<ValueType> experiment;
    private ExperimentResult<ValueType> experimentResult;
}
