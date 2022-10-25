package growthbook.sdk.java.models;

public class BlankUserAttributes implements UserAttributes {
    @Override
    public String toJson() {
        return "{}";
    }
}
