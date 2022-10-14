package growthbook.sdk.java.models;

/**
 * User Attributes can be any JSON serializable class that implements a .toJson() method
 */
public interface UserAttributes {
    /**
     * JSON representation of the attributes
     * @return JSON string
     */
    String toJson();
}
