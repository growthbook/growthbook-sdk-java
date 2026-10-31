package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.FeatureSnapshot;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.UserContextUtils;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <b>INTERNAL</b>: not part of the supported API. Public only because {@code GrowthBookClient} lives in
 * the parent package — treat its signatures as free to change without a major version bump.
 *
 * Internal coordinator for the client-level {@link GlobalContext}.
 * Keeps feature state updates and evaluation context creation outside the public facade.
 */
public final class GlobalContextManager {
    private final Options options;
    private final AtomicReference<GlobalContext> globalContext = new AtomicReference<>();

    /**
     * Guards the read-snapshot-then-publish sequence so {@link #initialize} and {@link #refresh}
     * cannot interleave. Without it, a seeded cold start can race its own background refresh:
     * {@code initialize} reads the seed, the background refresh publishes live flags, then
     * {@code initialize} overwrites them with the stale seed — and identical later responses do not
     * repair it because {@code featuresChanged} is false. Both paths read the repository's current
     * snapshot, so serializing read-and-publish is enough for the newer snapshot to win regardless
     * of ordering.
     */
    private final Object publishLock = new Object();

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
        publishSnapshot(repository);
    }

    /**
     * Updates feature and saved-group data after a repository refresh.
     *
     * @param repository repository containing the latest parsed feature data
     */
    public void refresh(GBFeaturesRepository repository) {
        publishSnapshot(repository);
    }

    private void publishSnapshot(GBFeaturesRepository repository) {
        synchronized (publishLock) {
            this.globalContext.set(createGlobalContext(repository));
        }
    }

    /**
     * Creates an evaluation context for a specific user by overlaying user attributes on global attributes.
     *
     * @param userContext per-user evaluation context
     * @return evaluation context used by feature and experiment evaluators
     */
    public EvaluationContext createEvaluationContext(UserContext userContext) {
        UserContext updatedUserContext = UserContextUtils.mergeAttributesAndPreloadSticky(this.options, userContext);
        return new EvaluationContext(
                currentGlobalContext(),
                updatedUserContext,
                new EvaluationContext.StackContext(),
                this.options
        );
    }

    /**
     * The managed context, or an empty one built from {@link Options} when the client has not
     * initialized yet or initialization failed.
     *
     * <p>Handing the evaluators a {@code null} global context makes every feature resolve to
     * {@code unknownFeature} — ignoring globally forced values, which do not need feature data —
     * and makes an experiment with a condition throw when it reads saved groups. The fallback is
     * built per call rather than cached so that options changed before initialization, such as
     * {@code setGlobalForceFeatures}, are picked up.
     */
    private GlobalContext currentGlobalContext() {
        GlobalContext current = this.globalContext.get();
        return current != null ? current : emptyGlobalContext();
    }

    private GlobalContext emptyGlobalContext() {
        return GlobalContext.builder()
                .features(Collections.emptyMap())
                .savedGroups(new JsonObject())
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }

    /**
     * @return number of features in the current global context (0 when uninitialized).
     */
    public int featureCount() {
        GlobalContext context = this.globalContext.get();
        if (context == null || context.getFeatures() == null) {
            return 0;
        }
        return context.getFeatures().size();
    }

    private GlobalContext createGlobalContext(GBFeaturesRepository repository) {
        // Read the payload as ONE snapshot: two separate getter calls could pair
        // new features with old saved groups if a refresh lands in between.
        FeatureSnapshot featureSnapshot = repository.getFeatureSnapshot();
        return GlobalContext.builder()
                .features(featureSnapshot.getParsedFeatures())
                .savedGroups(featureSnapshot.getParsedSavedGroups())
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }
}
