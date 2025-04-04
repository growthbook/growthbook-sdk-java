package growthbook.sdk.java.multiusermode.configurations;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashSet;
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

        public StackContext() {
            this.id = null;
            this.evaluatedFeatures = new HashSet<>();
        }
    }
}
