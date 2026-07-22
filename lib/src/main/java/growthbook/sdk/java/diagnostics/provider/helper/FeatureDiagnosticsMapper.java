package growthbook.sdk.java.diagnostics.provider.helper;

import growthbook.sdk.java.diagnostics.model.enums.FeatureDataSource;
import growthbook.sdk.java.diagnostics.model.FeatureDiagnostics;
import growthbook.sdk.java.diagnostics.model.FeatureKeyDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.FeatureState;
import growthbook.sdk.java.model.Feature;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds feature diagnostics from repository-visible feature state.
 */
public final class FeatureDiagnosticsMapper {
    private static final int FEATURE_KEY_SAMPLE_LIMIT = 20;

    public FeatureDiagnostics fromFeatureMap(
            FeatureDataSource source,
            boolean available,
            int activeFeatureCount,
            @Nullable Map<String, Feature<?>> features
    ) {
        FeatureKeyDiagnostics keys = featureKeys(features);
        int normalizedActiveCount = nonNegative(activeFeatureCount);
        int totalFeatureCount = Math.max(keys.getTotalCount(), normalizedActiveCount);

        return FeatureDiagnostics.builder()
                .state(featureState(available, totalFeatureCount))
                .source(source)
                .available(available)
                .activeFeatureCount(normalizedActiveCount)
                .totalFeatureCount(totalFeatureCount)
                .keys(keys)
                .build();
    }

    public FeatureDiagnostics fromCount(
            FeatureDataSource source,
            boolean available,
            int activeFeatureCount
    ) {
        int normalizedActiveCount = nonNegative(activeFeatureCount);
        return FeatureDiagnostics.builder()
                .state(featureState(available, normalizedActiveCount))
                .source(source)
                .available(available)
                .activeFeatureCount(normalizedActiveCount)
                .totalFeatureCount(normalizedActiveCount)
                .keys(unavailableFeatureKeys())
                .build();
    }

    public FeatureDiagnostics unavailable() {
        return fromCount(FeatureDataSource.NONE, false, 0);
    }

    private FeatureState featureState(boolean available, int totalFeatureCount) {
        if (!available) {
            return FeatureState.NOT_LOADED;
        }
        return totalFeatureCount == 0 ? FeatureState.LOADED_EMPTY : FeatureState.LOADED;
    }

    private FeatureKeyDiagnostics featureKeys(@Nullable Map<String, Feature<?>> features) {
        if (features == null) {
            return unavailableFeatureKeys();
        }

        List<String> sortedKeys = new ArrayList<>();
        for (String key : features.keySet()) {
            if (key != null) {
                sortedKeys.add(key);
            }
        }
        Collections.sort(sortedKeys);

        int sampleSize = Math.min(sortedKeys.size(), FEATURE_KEY_SAMPLE_LIMIT);
        List<String> sample = new ArrayList<>(sortedKeys.subList(0, sampleSize));

        return FeatureKeyDiagnostics.builder()
                .available(true)
                .totalCount(sortedKeys.size())
                .sampleLimit(FEATURE_KEY_SAMPLE_LIMIT)
                .sample(Collections.unmodifiableList(sample))
                .truncated(sortedKeys.size() > FEATURE_KEY_SAMPLE_LIMIT)
                .sha256(sha256(sortedKeys))
                .build();
    }

    private FeatureKeyDiagnostics unavailableFeatureKeys() {
        return FeatureKeyDiagnostics.builder()
                .available(false)
                .totalCount(0)
                .sampleLimit(FEATURE_KEY_SAMPLE_LIMIT)
                .sample(Collections.emptyList())
                .truncated(false)
                .sha256(null)
                .build();
    }

    private String sha256(List<String> sortedKeys) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String key : sortedKeys) {
                digest.update(key.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private int nonNegative(int value) {
        return Math.max(0, value);
    }
}
