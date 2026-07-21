package growthbook.sdk.java.diagnostics.model;

import javax.annotation.Nullable;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Diagnostics metadata for feature keys in the current snapshot.
 */
@Value
@Builder
public class FeatureKeyDiagnostics {
    /**
     * Whether feature keys were available to inspect.
     */
    boolean available;

    /**
     * Total number of feature keys in the inspected snapshot.
     */
    int totalCount;

    /**
     * Maximum number of keys included in {@link #sample}.
     */
    int sampleLimit;

    /**
     * Stable, sorted sample of feature keys.
     */
    List<String> sample;

    /**
     * Whether the key list was truncated to fit the sample limit.
     */
    boolean truncated;

    /**
     * SHA-256 hash of the sorted feature keys, or {@code null} when keys are unavailable.
     */
    @Nullable
    String sha256;
}
