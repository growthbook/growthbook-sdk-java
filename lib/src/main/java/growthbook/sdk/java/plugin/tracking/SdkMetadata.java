package growthbook.sdk.java.plugin.tracking;

/**
 * Identifying metadata for this SDK. Emitted on every tracked event and in
 * the {@code User-Agent} of outbound ingest requests.
 */
public final class SdkMetadata {

    public static final String LANGUAGE = "java";

    public static final String NAME = "growthbook-java-sdk";

    public static final String VERSION = resolveVersion();

    public static final String USER_AGENT = NAME + "/" + VERSION;

    private SdkMetadata() {
    }

    private static String resolveVersion() {
        String implVersion = SdkMetadata.class.getPackage().getImplementationVersion();
        if (implVersion != null && !implVersion.isEmpty()) {
            return implVersion;
        }
        return "unknown";
    }
}
