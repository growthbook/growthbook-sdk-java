package growthbook.sdk.java;

import javax.annotation.Nullable;

/**
 * The hashing algorithm version you'd like to use.
 *
 */
public enum HashVersion {
    /**
     * version 1 is the current and most widely-supported hash version.
     * @deprecated Use the latest hash version
     */
    V1(1),

    /**
     * version 2 is the latest and recommended hash version.
     */
    V2(2);

    private final Integer rawValue;

    HashVersion(Integer rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public String toString() {
        return this.rawValue.toString();
    }

    /**
     * The integer value of the hash version
     */
    public Integer intValue() {
        return this.rawValue;
    }

    public static @Nullable HashVersion fromInt(Integer intValue) {
        for (HashVersion o : values()) {
            if (o.rawValue.equals(intValue)) {
                return o;
            }
        }

        return null;
    }
}
