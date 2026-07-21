package growthbook.sdk.java.diagnostics.model;

import growthbook.sdk.java.diagnostics.model.enums.CacheState;
import growthbook.sdk.java.sandbox.CacheMode;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;

/**
 * Feature cache metadata.
 */
@Value
@Builder
public class CacheDiagnostics {
    /**
     * Derived cache state for support/debugging flows.
     */
    CacheState state;

    /**
     * Configured cache mode from client options.
     */
    CacheMode mode;

    /**
     * Whether feature cache persistence is enabled for the active repository/configuration.
     */
    boolean enabled;

    /**
     * Epoch-millis timestamp of the last cache update, or {@code 0} when unavailable.
     */
    long lastUpdatedMillis;

    /**
     * Age in milliseconds since the last cache update.
     */
    @Nullable
    Long ageMillis;
}
