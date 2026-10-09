package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A single deferred experiment-exposure tracking call: the {@link Experiment}, its
 * {@link ExperimentResult}, and the {@link UserContext} it was evaluated for.
 *
 * <p>Produced by {@link UserScopedGrowthBook} while a request buffers exposures and replayed by
 * {@link UserScopedGrowthBook#fireDeferredTrackingCalls()}. It can also be serialized between
 * processes (evaluate on the server, replay on the client) via
 * {@link UserScopedGrowthBook#getDeferredTrackingCalls()} /
 * {@link UserScopedGrowthBook#setDeferredTrackingCalls(java.util.List)}.
 *
 * @param <T> the experiment value type; the experiment and result always share it
 */
@AllArgsConstructor
@Getter
public class DeferredTrackingCall<T> {
    private final Experiment<T> experiment;
    private final ExperimentResult<T> result;
    private final UserContext userContext;
}
