package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.model.FeatureResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Slf4j
@AllArgsConstructor
public class EvaluationContext {

    private GlobalContext global;
    private UserContext user;
    private StackContext stack;
    private Options options;

    @Data
    public static class StackContext { // FeatureEvalContext
        @Nullable
        private String id;
        private Set<String> evaluatedFeatures;
        private Map<String, FeatureResult<?>> memoizedResults;

        public StackContext() {
            this.id = null;
            this.evaluatedFeatures = new HashSet<>();
            this.memoizedResults = new HashMap<>();
        }
    }
}
