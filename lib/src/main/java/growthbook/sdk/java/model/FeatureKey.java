package growthbook.sdk.java.model;

/**
 * A type-safe reference to a feature flag.
 *
 * <p>Instead of passing raw string keys such as {@code growthBook.isOn("new-home")}, callers
 * declare their feature keys once and reference them through this interface:
 *
 * <pre>{@code
 * public final class Features {
 *     public static final FeatureKey<Boolean> NEW_HOME = TypedKey.ofBoolean("new-home");
 *     public static final FeatureKey<String>  THEME    = TypedKey.ofString("theme");
 *     private Features() {}
 * }
 *
 * growthBook.getFeature(Features.NEW_HOME);       // FeatureResult<Boolean>
 * growthBook.getBooleanFeature(Features.NEW_HOME);
 * }</pre>
 *
 * <p>The key carries both the feature identifier and its value type, so a mistyped string key
 * becomes a compile-time error rather than a silent runtime miss.
 *
 * @param <T> the value type the feature evaluates to
 * @see TypedKey
 */
public interface FeatureKey<T> {

    /**
     * The feature identifier as configured in GrowthBook, e.g. {@code "new-home"}.
     *
     * @return the raw feature key
     */
    String getKey();

    /**
     * The Gson-deserializable class of the feature value, e.g. {@code Boolean.class}.
     *
     * @return the value type
     */
    Class<T> getValueType();
}
