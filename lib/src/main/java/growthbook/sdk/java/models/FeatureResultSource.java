package growthbook.sdk.java.models;

import com.google.gson.annotations.SerializedName;

public enum FeatureResultSource {
    @SerializedName("unknownFeature") UNKNOWN_FEATURE("unknownFeature"),
    @SerializedName("defaultValue") DEFAULT_VALUE("defaultValue"),
    @SerializedName("force") FORCE("force"),
    @SerializedName("experiment") EXPERIMENT("experiment"),
    ;
    private final String rawValue;

    FeatureResultSource(String rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public String toString() {
        return this.rawValue;
    }
}
