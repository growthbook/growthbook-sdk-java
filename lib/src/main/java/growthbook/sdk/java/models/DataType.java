package growthbook.sdk.java.models;

/**
 * A data type class used internally to help evaluate conditions
 */
public enum DataType {
    /**
     * when the type is a string
     */
    STRING("string"),

    /**
     * when the type is a number
     */
    NUMBER("number"),

    /**
     * when the type is a boolean
     */
    BOOLEAN("boolean"),

    /**
     * when the type is an array
     */
    ARRAY("array"),

    /**
     * when the type is an object
     */
    OBJECT("object"),

    /**
     * when the type is a JSON null
     */
    NULL("null"),

    /**
     * when the type is not present, e.g. field doesn't exist or is a Java null
     */
    UNDEFINED("undefined"),

    /**
     * when the type is unknown. Can occur when there is a deserialization error.
     */
    UNKNOWN("unknown"),
    ;
    private final String rawValue;

    DataType(String rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public String toString() {
        return this.rawValue;
    }
}
