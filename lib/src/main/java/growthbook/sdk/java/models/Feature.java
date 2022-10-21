package growthbook.sdk.java.models;

public class Feature<T> {
    final Class<T> typeParameterClass;

    private T defaultValue;
    public Feature(T defaultValue, Class<T> typeParameterClass) {
        this.defaultValue = defaultValue;
        this.typeParameterClass = typeParameterClass;
    }
}
