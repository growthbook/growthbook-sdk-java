package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.plugin.PluginRegistry;
import growthbook.sdk.java.stickyBucketing.StickyBucketDocWriter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Slf4j
public class EvaluationContext {

    private GlobalContext global;
    private UserContext user;
    private StackContext stack;
    private Options options;

    /**
     * Plugins registered with the owning GrowthBook instance. Carried per
     * evaluation context (not on the shared {@link Options}) so that separate
     * SDK instances built from the same {@code Options} stay isolated.
     * Excluded from equals/hashCode/toString: identity-equality object, and its
     * toString is noise in debug logs.
     */
    @Nullable
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private PluginRegistry pluginRegistry;

    /**
     * Persistence seam for newly assigned sticky bucket documents, set by the
     * owning multi-user client. When null the evaluator calls the configured
     * synchronous service directly (legacy path, unchanged). Excluded from
     * equals/hashCode/toString: lambdas have identity equality.
     */
    @Nullable
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private StickyBucketDocWriter stickyBucketDocWriter;

    public EvaluationContext(GlobalContext global, UserContext user, StackContext stack, Options options) {
        this.global = global;
        this.user = user;
        this.stack = stack;
        this.options = options;
    }

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
