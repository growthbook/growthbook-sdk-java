package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.model.FeatureResult;
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

    public EvaluationContext(GlobalContext global, UserContext user, StackContext stack, Options options) {
        this.global = global;
        this.user = user;
        this.stack = stack;
        this.options = options;
    }

    /**
     * Resolves the URL used for URL-based targeting and query-string overrides, preferring the
     * per-user {@link UserContext#getUrl()} (e.g. set through a user-scoped instance) over the
     * shared client-level {@link Options#getUrl()}. Returns {@code null} when neither is set.
     *
     * @return the per-user URL when present, otherwise the client-level URL, or {@code null}
     */
    @Nullable
    public String resolveUrl() {
        String userUrl = this.user != null ? this.user.getUrl() : null;
        if (userUrl != null) {
            return userUrl;
        }
        return this.options != null ? this.options.getUrl() : null;
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
