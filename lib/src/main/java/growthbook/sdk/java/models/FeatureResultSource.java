package growthbook.sdk.java.models;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

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


    /**
     * Get a nullable enum Operator from the string value. Use this instead of valueOf()
     * @param stringValue string to try to parse as an operator
     * @return nullable Operator
     */
    public static @Nullable FeatureResultSource fromString(String stringValue) {
        for (FeatureResultSource o : values()) {
            if (o.rawValue.equals(stringValue)) {
                return o;
            }
        }

        return null;
    }
}
