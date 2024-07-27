package growthbook.sdk.java;

import javax.annotation.Nullable;

/**
 * Operator for use in the condition JSON
 */
public enum Operator {
    /**
     * $in
     */
    IN("$in"),
    /**
     * $nin
     */
    NIN("$nin"),
    /**
     * $gt
     */
    GT("$gt"),
    /**
     * $gte
     */
    GTE("$gte"),
    /**
     * $lt
     */
    LT("$lt"),
    /**
     * $lte
     */
    LTE("$lte"),
    /**
     * $regex
     */
    REGEX("$regex"),
    /**
     * $ne
     */
    NE("$ne"),
    /**
     * $eq
     */
    EQ("$eq"),
    /**
     * $size
     */
    SIZE("$size"),
    /**
     * $elemMatch
     */
    ELEMENT_MATCH("$elemMatch"),
    /**
     * $all
     */
    ALL("$all"),
    /**
     * $not
     */
    NOT("$not"),
    /**
     * $type
     */
    TYPE("$type"),
    /**
     * $exists
     */
    EXISTS("$exists"),

    /**
     * $vgt
     */
    VERSION_GT("$vgt"),
    /**
     * $vgte
     */
    VERSION_GTE("$vgte"),
    /**
     * $vlt
     */
    VERSION_LT("$vlt"),
    /**
     * $vlte
     */
    VERSION_LTE("$vlte"),
    /**
     * $vne
     */
    VERSION_NE("$vne"),
    /**
     * $veq
     */
    VERSION_EQ("$veq"),
    /**
     * $inGroup
     */
    IN_GROUP("$inGroup"),
    /**
     * $notInGroup
     */
    NOT_IN_GROUP("$notInGroup")
    ;

    private final String rawValue;

    Operator(String rawValue) {
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
    public static @Nullable Operator fromString(String stringValue) {
        for (Operator o : values()) {
            if (o.rawValue.equals(stringValue)) {
                return o;
            }
        }

        return null;
    }
}
