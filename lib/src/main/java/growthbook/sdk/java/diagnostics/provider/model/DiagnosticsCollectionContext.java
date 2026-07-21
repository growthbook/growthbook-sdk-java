package growthbook.sdk.java.diagnostics.provider.model;

import growthbook.sdk.java.diagnostics.provider.helper.DiagnosticsSecretMasker;
import growthbook.sdk.java.repository.GBFeaturesRepository;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;

/**
 * Runtime context used while collecting a diagnostics snapshot.
 */
@Value
@Builder
public class DiagnosticsCollectionContext {
    long generatedAtMillis;

    @Nullable
    GBFeaturesRepository repository;

    DiagnosticsSecretMasker secretMasker;
}
