package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.DeferredTrackingBuffer;
import growthbook.sdk.java.plugin.PluginRegistry;
import lombok.Data;
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
     */
    @Nullable
    private PluginRegistry pluginRegistry;

    /**
     * When non-null, experiment exposures are buffered here instead of firing immediately, so a
     * per-request scope can flush tracking once at the end. Null on the immediate path.
     */
    @Nullable
    private DeferredTrackingBuffer deferredTracking;

    public EvaluationContext(GlobalContext global, UserContext user, StackContext stack, Options options) {
        this(global, user, stack, options, null);
    }

    public EvaluationContext(GlobalContext global, UserContext user, StackContext stack, Options options,
                             @Nullable DeferredTrackingBuffer deferredTracking) {
        this.global = global;
        this.user = user;
        this.stack = stack;
        this.options = options;
        this.deferredTracking = deferredTracking;
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
