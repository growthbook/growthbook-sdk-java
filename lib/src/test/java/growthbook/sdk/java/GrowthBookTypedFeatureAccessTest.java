package growthbook.sdk.java;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.TypedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookTypedFeatureAccessTest {

    private static final String FEATURES_JSON = "{"
            + "\"new-home\":{\"defaultValue\":true},"
            + "\"dark-mode\":{\"defaultValue\":false},"
            + "\"theme\":{\"defaultValue\":\"dark\"},"
            + "\"max-items\":{\"defaultValue\":25},"
            + "\"ratio\":{\"defaultValue\":1.5},"
            + "\"weight\":{\"defaultValue\":2.5},"
            + "\"home-config\":{\"defaultValue\":{\"title\":\"Welcome\",\"limit\":5}}"
            + "}";

    private static final TypedKey<String> THEME = TypedKey.ofString("theme");
    private static final TypedKey<Double> RATIO = TypedKey.ofDouble("ratio");
    private static final TypedKey<Float> WEIGHT = TypedKey.ofFloat("weight");
    private static final TypedKey<Boolean> NEW_HOME = TypedKey.ofBoolean("new-home");
    private static final TypedKey<Boolean> DARK_MODE = TypedKey.ofBoolean("dark-mode");
    private static final TypedKey<Integer> MAX_ITEMS = TypedKey.ofInteger("max-items");
    private static final TypedKey<HomeConfig> HOME_CONFIG = TypedKey.of("home-config", HomeConfig.class);

    private final GrowthBook subject = new GrowthBook(GBContext.builder().featuresJson(FEATURES_JSON).build());

    @Test
    @DisplayName("A typed key exposes its raw key and value type")
    void typedKeyExposesKeyAndValueType() {
        // When
        TypedKey<Boolean> key = TypedKey.ofBoolean("new-home");

        // Then
        assertEquals("new-home", key.getKey());
        assertEquals(Boolean.class, key.getValueType());
        assertEquals(String.class, THEME.getValueType());
    }

    @Test
    @DisplayName("A typed key rejects a null key or value type")
    void typedKeyRejectsNullArguments() {
        // When / Then
        NullPointerException nullKey = assertThrows(NullPointerException.class, () -> TypedKey.of(null, Boolean.class));
        NullPointerException nullType = assertThrows(NullPointerException.class, () -> TypedKey.of("feature-1", null));
        assertEquals("feature key must not be null", nullKey.getMessage());
        assertEquals("feature value type must not be null", nullType.getMessage());
    }

    @Test
    @DisplayName("Typed keys are equal when key and value type are equal")
    void typedKeysAreEqualByKeyAndValueType() {
        // Given
        TypedKey<Boolean> key = TypedKey.ofBoolean("new-home");

        // When / Then
        assertEquals(TypedKey.ofBoolean("new-home"), key);
        assertEquals(TypedKey.ofBoolean("new-home").hashCode(), key.hashCode());
        assertNotEquals(TypedKey.ofBoolean("other"), key);
        assertNotEquals(TypedKey.ofString("new-home"), key);
        assertNotEquals(null, key);
    }

    @Test
    @DisplayName("A typed key prints its key and simple value type name")
    void typedKeyPrintsKeyAndValueType() {
        // When
        String text = NEW_HOME.toString();

        // Then
        assertEquals("TypedKey{key='new-home', valueType=Boolean}", text);
    }

    @Test
    @DisplayName("getFeature returns a typed result for a known key")
    void getFeatureReturnsTypedResult() {
        // When
        FeatureResult<Boolean> result = subject.getFeature(NEW_HOME);

        // Then
        assertEquals(Boolean.TRUE, result.getValue());
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
        assertTrue(result.isOn());
    }

    @Test
    @DisplayName("getFeature returns an unknown feature result for an unknown key")
    void getFeatureReturnsUnknownFeatureResultForUnknownKey() {
        // When
        FeatureResult<Boolean> result = subject.getFeature(TypedKey.ofBoolean("missing"));

        // Then
        assertNull(result.getValue());
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, result.getSource());
        assertFalse(result.isOn());
        assertTrue(result.isOff());
    }

    @Test
    @DisplayName("getFeatureValue deserializes a complex value type and falls back for unknown keys")
    void getFeatureValueDeserializesComplexType() {
        // Given
        HomeConfig fallback = new HomeConfig("fallback", 0);

        // When
        HomeConfig config = subject.getFeatureValue(HOME_CONFIG, fallback);
        HomeConfig missing = subject.getFeatureValue(TypedKey.of("missing", HomeConfig.class), fallback);

        // Then
        assertEquals(new HomeConfig("Welcome", 5), config);
        assertEquals(fallback, missing);
    }

    @Test
    @DisplayName("A feature with a falsy value is off")
    void featureWithFalsyValueIsOff() {
        // When / Then
        assertFalse(subject.isOn(DARK_MODE));
        assertTrue(subject.isOff(DARK_MODE));
        assertFalse(subject.getBooleanFeature(DARK_MODE));
        assertFalse(subject.getBooleanFeature(DARK_MODE, true));
    }

    @Test
    @DisplayName("isOn and isOff accept a typed key")
    void isOnAndIsOffAcceptTypedKey() {
        // When / Then
        assertTrue(subject.isOn(NEW_HOME));
        assertFalse(subject.isOff(NEW_HOME));
    }

    @Test
    @DisplayName("getBooleanFeature returns the value and defaults to false for unknown keys")
    void getBooleanFeatureReturnsValueOrDefault() {
        // When / Then
        assertTrue(subject.getBooleanFeature(NEW_HOME));
        assertTrue(subject.getBooleanFeature(NEW_HOME, false));
        assertFalse(subject.getBooleanFeature(TypedKey.ofBoolean("missing")));
        assertTrue(subject.getBooleanFeature(TypedKey.ofBoolean("missing"), true));
    }

    @Test
    @DisplayName("Typed getters read string, integer, double and float values")
    void typedGettersReadValues() {
        // When / Then
        assertEquals("dark", subject.getStringFeature(THEME, "light"));
        assertEquals(Integer.valueOf(25), subject.getIntegerFeature(MAX_ITEMS, 10));
        assertEquals(Double.valueOf(1.5), subject.getDoubleFeature(RATIO, 0.0));
        assertEquals(Float.valueOf(2.5f), subject.getFloatFeature(WEIGHT, 0.0f));
    }

    @Test
    @DisplayName("Typed getters fall back to the default for unknown keys")
    void typedGettersFallBackToDefault() {
        // When / Then
        assertEquals("light", subject.getStringFeature(TypedKey.ofString("missing"), "light"));
        assertEquals(Integer.valueOf(10), subject.getIntegerFeature(TypedKey.ofInteger("missing"), 10));
        assertEquals(Double.valueOf(9.9), subject.getDoubleFeature(TypedKey.ofDouble("missing"), 9.9));
        assertEquals(Float.valueOf(9.9f), subject.getFloatFeature(TypedKey.ofFloat("missing"), 9.9f));
    }

    @Test
    @DisplayName("getFeatureValue infers the deserialization class from the key")
    void getFeatureValueInfersClassFromKey() {
        // When / Then
        assertEquals("dark", subject.getFeatureValue(THEME, "light"));
        assertEquals(Integer.valueOf(25), subject.getFeatureValue(MAX_ITEMS, 0));
    }

    private static final class HomeConfig {
        private final String title;
        private final int limit;

        HomeConfig(String title, int limit) {
            this.title = title;
            this.limit = limit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HomeConfig)) {
                return false;
            }
            HomeConfig other = (HomeConfig) o;
            return limit == other.limit && Objects.equals(title, other.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, limit);
        }
    }
}
