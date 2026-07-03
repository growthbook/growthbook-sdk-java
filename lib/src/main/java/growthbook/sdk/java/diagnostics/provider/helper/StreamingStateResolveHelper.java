package growthbook.sdk.java.diagnostics.provider.helper;

import growthbook.sdk.java.diagnostics.model.enums.StreamingState;
import growthbook.sdk.java.diagnostics.provider.model.StreamingStatusSnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Classifies raw streaming signals into a single diagnostics state.
 */
public final class StreamingStateResolveHelper {

    private static final StreamingState DEFAULT_STATE = StreamingState.INITIALIZING;

    private static final List<StreamingStateRule> STATE_RULES = Arrays.asList(
            streamingStateRule(status -> !status.isConfigured(), StreamingState.DISABLED),
            streamingStateRule(StreamingStatusSnapshot::isShutdown, StreamingState.SHUTDOWN),
            streamingStateRule(status -> !status.isInitialized(), StreamingState.INITIALIZING),
            streamingStateRule(StreamingStatusSnapshot::isConnected, StreamingState.CONNECTED),
            streamingStateRule(status -> !status.isAllowed(), StreamingState.UNSUPPORTED),
            streamingStateRule(status -> status.getReconnectAttempts() > 0, StreamingState.INTERRUPTED)
    );

    public StreamingState resolve(StreamingStatusSnapshot status) {
        for (StreamingStateRule rule : STATE_RULES) {
            if (rule.matches(status)) {
                return rule.state;
            }
        }
        return DEFAULT_STATE;
    }

    private static StreamingStateRule streamingStateRule(
            Predicate<StreamingStatusSnapshot> predicate,
            StreamingState state
    ) {
        return new StreamingStateRule(predicate, state);
    }

    private static final class StreamingStateRule {
        private final StreamingState state;
        private final Predicate<StreamingStatusSnapshot> predicate;

        private StreamingStateRule(Predicate<StreamingStatusSnapshot> predicate, StreamingState state) {
            this.predicate = predicate;
            this.state = state;
        }

        private boolean matches(StreamingStatusSnapshot status) {
            return this.predicate.test(status);
        }
    }
}
