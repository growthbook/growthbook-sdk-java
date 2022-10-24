package growthbook.sdk.java.models;

public enum DataType {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object"),
    NULL("null"),
    UNDEFINED("undefined"),
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
