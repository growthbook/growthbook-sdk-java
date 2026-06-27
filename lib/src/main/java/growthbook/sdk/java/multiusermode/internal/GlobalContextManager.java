package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal coordinator for the client-level {@link GlobalContext}.
 * Keeps feature state updates and evaluation context creation outside of the public facade.
 */
public final class GlobalContextManager {
    private final Options options;
    private final AtomicReference<GlobalContext> globalContext = new AtomicReference<>();

    /**
     * Creates a manager bound to the client options instance.
     *
     * @param options client options used to build evaluation contexts
     */
    public GlobalContextManager(Options options) {
        this.options = options;
    }

    /**
     * Replaces the managed context with feature data from the initialized repository.
     *
     * @param repository initialized feature repository
     */
    public void initialize(GBFeaturesRepository repository) {
        this.globalContext.set(createGlobalContext(repository));
    }

    /**
     * Updates feature and saved-group data after a repository refresh.
     *
     * @param repository repository containing the latest parsed feature data
     */
    public void refresh(GBFeaturesRepository repository) {
        this.globalContext.set(createGlobalContext(repository));
    }

    /**
     * Creates an evaluation context for a specific user by overlaying user attributes on global attributes.
     *
     * @param userContext per-user evaluation context
     * @return evaluation context used by feature and experiment evaluators
     */
    public EvaluationContext createEvaluationContext(UserContext userContext) {
        UserContext updatedUserContext = userContext.withAttributes(mergeAttributes(userContext));
        return new EvaluationContext(
                this.globalContext.get(),
                updatedUserContext,
                new EvaluationContext.StackContext(),
                this.options
        );
    }

    private GlobalContext createGlobalContext(GBFeaturesRepository repository) {
        return GlobalContext.builder()
                .features(repository.getParsedFeatures())
                .savedGroups(repository.getParsedSavedGroups())
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }

    private JsonObject mergeAttributes(UserContext userContext) {
        JsonObject merged = getGlobalAttributes();
        JsonObject userAttributes = userContext.getAttributes();
        if (userAttributes != null) {
            for (Map.Entry<String, JsonElement> entry : userAttributes.entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private JsonObject getGlobalAttributes() {
        if (this.options.getGlobalAttributes() == null) {
            return new JsonObject();
        }

        JsonObject globalAttributes = GrowthBookJsonUtils.getInstance()
                .gson
                .fromJson(this.options.getGlobalAttributes(), JsonObject.class);
        return globalAttributes == null ? new JsonObject() : globalAttributes;
    }
}
