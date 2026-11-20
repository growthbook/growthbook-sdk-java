package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
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
    /**
     * @deprecated evaluation keeps its own per-call state and never reads this.
     */
    @Deprecated
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
     * Exposure de-duplication shared by every evaluation of the owning SDK instance, carried
     * here for the same isolation reason as {@link #pluginRegistry}. Defaults to a tracker owned
     * by this context; when set to {@code null}, every exposure is reported. Excluded from
     * equals/hashCode/toString: identity-equality object.
     */
    @Nullable
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ExperimentTracker experimentTracker = new ExperimentTracker();

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

    /**
     * When true (set by the multi-user client), a throwing tracking callback is
     * logged and its exposure retried instead of propagating out of evaluation —
     * an analytics failure must not fail the assignment (JS/Python parity).
     * False keeps the single-user {@code GrowthBook} behavior: the exception
     * propagates and the exposure is not retried.
     */
    private boolean suppressTrackingErrors;

    public EvaluationContext(GlobalContext global, UserContext user, Options options) {
        this(global, user, new StackContext(), options);
    }

    /**
     * @deprecated evaluation keeps its own per-call state and never reads {@code stack}; use
     * {@link #EvaluationContext(GlobalContext, UserContext, Options)}.
     */
    @Deprecated
    public EvaluationContext(GlobalContext global, UserContext user, StackContext stack, Options options) {
        this.global = global;
        this.user = user;
        this.stack = stack;
        this.options = options;
    }

    /**
     * @return the exposure tracker
     * @deprecated use {@link #getExperimentTracker()}
     */
    @Deprecated
    @Nullable
    public ExperimentTracker getSharedExperimentTracker() {
        return experimentTracker;
    }

    /**
     * @param sharedExperimentTracker the exposure tracker
     * @deprecated use {@link #setExperimentTracker(ExperimentTracker)}
     */
    @Deprecated
    public void setSharedExperimentTracker(@Nullable ExperimentTracker sharedExperimentTracker) {
        this.experimentTracker = sharedExperimentTracker;
    }

    /**
     * @deprecated evaluation keeps its own per-call state and never reads this.
     */
    @Deprecated
    @Data
    public static class StackContext {
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
