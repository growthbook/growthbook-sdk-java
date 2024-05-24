package growthbook.sdk.java;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class FeatureEvalContext {
    private String id;
    private Set<String> evaluatedFeatures;
}
