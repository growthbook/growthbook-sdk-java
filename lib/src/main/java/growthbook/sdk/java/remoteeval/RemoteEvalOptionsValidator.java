package growthbook.sdk.java.remoteeval;

import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Locale;

/**
 * Validates SDK options before enabling remote evaluation.
 */
public final class RemoteEvalOptionsValidator {
    private static final String DEFAULT_SCHEME = "https://";
    private static final String SCHEME_SEPARATOR = "://";
    private static final String GROWTHBOOK_CLOUD_DOMAIN = "growthbook.io";

    private RemoteEvalOptionsValidator() {
    }

    public static void validate(Options options) {
        if (options == null || !options.isRemoteEvalEnabled()) {
            return;
        }

        validateCommon(
                options.getApiHost(),
                options.getClientKey(),
                options.getDecryptionKey(),
                options.getStickyBucketService() != null,
                options.getRefreshStrategy()
        );
    }

    public static void validate(GBContext context) {
        if (context == null || !context.isRemoteEvalEnabled()) {
            return;
        }

        validateCommon(
                context.getApiHost(),
                context.getClientKey(),
                context.getEncryptionKey(),
                context.getStickyBucketService() != null,
                null
        );
    }

    private static void validateCommon(
            @Nullable String apiHost,
            @Nullable String clientKey,
            @Nullable String decryptionKey,
            boolean hasStickyBucketService,
            @Nullable FeatureRefreshStrategy refreshStrategy
    ) {
        if (isBlank(apiHost)) {
            throw new IllegalArgumentException("apiHost is required when remoteEval is enabled");
        }
        if (isBlank(clientKey)) {
            throw new IllegalArgumentException("clientKey is required when remoteEval is enabled");
        }
        if (!isBlank(decryptionKey)) {
            throw new IllegalArgumentException("decryptionKey is not supported when remoteEval is enabled");
        }
        if (hasStickyBucketService) {
            throw new IllegalArgumentException("stickyBucketService is not supported when remoteEval is enabled");
        }
        if (refreshStrategy == FeatureRefreshStrategy.STALE_WHILE_REVALIDATE) {
            throw new IllegalArgumentException("stale-while-revalidate is not supported when remoteEval is enabled");
        }
        if (isGrowthBookCloudHost(apiHost)) {
            throw new IllegalArgumentException("remoteEval requires a self-hosted GrowthBook proxy or edge API host");
        }
    }

    private static boolean isGrowthBookCloudHost(String apiHost) {
        try {
            String raw = apiHost == null ? "" : apiHost.trim();
            String normalized = raw.contains(SCHEME_SEPARATOR) ? raw : DEFAULT_SCHEME + raw;
            String host = URI.create(normalized).getHost();
            if (host == null) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            return lowerHost.equals(GROWTHBOOK_CLOUD_DOMAIN)
                    || lowerHost.endsWith("." + GROWTHBOOK_CLOUD_DOMAIN);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
