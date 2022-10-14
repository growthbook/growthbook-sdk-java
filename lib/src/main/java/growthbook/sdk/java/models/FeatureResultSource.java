package growthbook.sdk.java.models;

public enum FeatureResultSource {
    UNKNOWN_FEATURE("unknownFeature"),
    DEFAULT_VALUE("defaultValue"),
    FORCE("force"),
    EXPERIMENT("experiment"),
    ;
    private final String rawValue;

    FeatureResultSource(String rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public String toString() {
        return rawValue;
    }
}
