package growthbook.sdk.java.model;

import java.util.Objects;

/**
 * Default immutable implementation of {@link FeatureKey}.
 *
 * <p>Declare feature keys as constants and reference them wherever a feature is evaluated:
 *
 * <pre>{@code
 * public final class Features {
 *     public static final TypedKey<Boolean> NEW_HOME  = TypedKey.ofBoolean("new-home");
 *     public static final TypedKey<String>  THEME     = TypedKey.ofString("theme");
 *     public static final TypedKey<Integer> MAX_ITEMS = TypedKey.ofInteger("max-items");
 *     public static final TypedKey<MyConfig> CONFIG   = TypedKey.of("my-config", MyConfig.class);
 *     private Features() {}
 * }
 * }</pre>
 *
 * @param <T> the value type the feature evaluates to
 */
public final class TypedKey<T> implements FeatureKey<T> {

    private final String key;
    private final Class<T> valueType;

    private TypedKey(String key, Class<T> valueType) {
        this.key = Objects.requireNonNull(key, "feature key must not be null");
        this.valueType = Objects.requireNonNull(valueType, "feature value type must not be null");
    }

    /**
     * Creates a typed key for an arbitrary Gson-deserializable value type.
     *
     * @param key       the feature identifier
     * @param valueType the value type class, e.g. {@code MyConfig.class}
     * @param <T> the value type
     * @return a new typed key
     */
    public static <T> TypedKey<T> of(String key, Class<T> valueType) {
        return new TypedKey<>(key, valueType);
    }

    /**
     * Creates a typed key for a boolean feature.
     *
     * @param key the feature identifier
     * @return a new typed key
     */
    public static TypedKey<Boolean> ofBoolean(String key) {
        return of(key, Boolean.class);
    }

    /**
     * Creates a typed key for a string feature.
     *
     * @param key the feature identifier
     * @return a new typed key
     */
    public static TypedKey<String> ofString(String key) {
        return of(key, String.class);
    }

    /**
     * Creates a typed key for an integer feature.
     *
     * @param key the feature identifier
     * @return a new typed key
     */
    public static TypedKey<Integer> ofInteger(String key) {
        return of(key, Integer.class);
    }

    /**
     * Creates a typed key for a double feature.
     *
     * @param key the feature identifier
     * @return a new typed key
     */
    public static TypedKey<Double> ofDouble(String key) {
        return of(key, Double.class);
    }

    /**
     * Creates a typed key for a float feature.
     *
     * @param key the feature identifier
     * @return a new typed key
     */
    public static TypedKey<Float> ofFloat(String key) {
        return of(key, Float.class);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Class<T> getValueType() {
        return valueType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypedKey)) {
            return false;
        }
        TypedKey<?> other = (TypedKey<?>) o;
        return key.equals(other.key) && valueType.equals(other.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, valueType);
    }

    @Override
    public String toString() {
        return "TypedKey{key='" + key + "', valueType=" + valueType.getSimpleName() + "}";
    }
}
