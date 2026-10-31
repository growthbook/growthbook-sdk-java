package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.TypedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypedFeatureAccessTest {

    @Test
    @DisplayName("TypedKey exposes the raw key and value type")
    void typedKey_exposesKeyAndType() {
        assertEquals("new-home", Features.NEW_HOME.getKey());
        assertEquals(Boolean.class, Features.NEW_HOME.getValueType());
        assertEquals(String.class, Features.THEME.getValueType());
    }

    @Test
    @DisplayName("TypedKey.of rejects null arguments")
    void typedKey_rejectsNulls() {
        assertThrows(NullPointerException.class, () -> TypedKey.of(null, Boolean.class));
        assertThrows(NullPointerException.class, () -> TypedKey.of("x", null));
    }

    @Test
    @DisplayName("TypedKey equality is based on key and value type")
    void typedKey_equality() {
        TypedKey<Boolean> key = TypedKey.ofBoolean("new-home");

        assertEquals(TypedKey.ofBoolean("new-home"), TypedKey.ofBoolean("new-home"));
        assertEquals(TypedKey.ofBoolean("new-home").hashCode(), TypedKey.ofBoolean("new-home").hashCode());
        assertNotEquals(TypedKey.ofBoolean("new-home"), TypedKey.ofBoolean("other"));
        assertNotEquals(null, key);
    }

    @Test
    @DisplayName("TypedKey.toString includes the key and value type")
    void typedKey_toString() {
        String text = Features.NEW_HOME.toString();

        assertTrue(text.contains("new-home"), text);
        assertTrue(text.contains("Boolean"), text);
    }

    @Test
    @DisplayName("getFeature returns a typed FeatureResult")
    void getFeature_returnsTypedResult() {
        FeatureResult<Boolean> result = growthBook().getFeature(Features.NEW_HOME);

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.getValue());
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
        assertTrue(result.isOn());
    }

    @Test
    @DisplayName("getFeature on an unknown key returns an unknownFeature result rather than null")
    void getFeature_unknownKey_returnsUnknownFeatureResult() {
        FeatureResult<Boolean> result = growthBook().getFeature(TypedKey.ofBoolean("missing"));

        assertNotNull(result);
        assertNull(result.getValue());
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, result.getSource());
        assertFalse(result.isOn());
        assertTrue(result.isOff());
    }

    @Test
    @DisplayName("getFeatureValue deserializes a complex value type, falling back on unknown keys")
    void getFeatureValue_complexType() {
        GrowthBook subject = growthBook();
        HomeConfig fallback = new HomeConfig("fallback", 0);

        HomeConfig config = subject.getFeatureValue(Features.HOME_CONFIG, fallback);
        assertEquals("Welcome", config.getTitle());
        assertEquals(5, config.getLimit());

        HomeConfig missing = subject.getFeatureValue(TypedKey.of("missing", HomeConfig.class), fallback);
        assertEquals(fallback, missing);
    }

    @Test
    @DisplayName("A feature whose value is falsy reports off")
    void offFeature_reportsOff() {
        GrowthBook subject = growthBook();

        assertFalse(subject.isOn(Features.DARK_MODE));
        assertTrue(subject.isOff(Features.DARK_MODE));
        assertFalse(subject.getBooleanFeature(Features.DARK_MODE));
        assertFalse(subject.getBooleanFeature(Features.DARK_MODE, true));
    }

    @Test
    @DisplayName("isOn / isOff accept a typed key")
    void isOnIsOff_acceptTypedKey() {
        GrowthBook subject = growthBook();

        assertTrue(subject.isOn(Features.NEW_HOME));
        assertFalse(subject.isOff(Features.NEW_HOME));
    }

    @Test
    @DisplayName("getBooleanFeature returns the value, defaulting to false when unknown")
    void getBooleanFeature() {
        GrowthBook subject = growthBook();

        assertTrue(subject.getBooleanFeature(Features.NEW_HOME));
        assertTrue(subject.getBooleanFeature(Features.NEW_HOME, false));
        assertFalse(subject.getBooleanFeature(TypedKey.ofBoolean("missing")));
        assertTrue(subject.getBooleanFeature(TypedKey.ofBoolean("missing"), true));
    }

    @Test
    @DisplayName("Typed getters read string, integer, double and float values")
    void typedGetters_readValues() {
        GrowthBook subject = growthBook();

        assertEquals("dark", subject.getStringFeature(Features.THEME, "light"));
        assertEquals(Integer.valueOf(25), subject.getIntegerFeature(Features.MAX_ITEMS, 10));
        assertEquals(Double.valueOf(1.5), subject.getDoubleFeature(Features.RATIO, 0.0));
        assertEquals(Float.valueOf(2.5f), subject.getFloatFeature(Features.WEIGHT, 0.0f));
    }

    @Test
    @DisplayName("Typed getters fall back to the default for unknown keys")
    void typedGetters_fallBackToDefault() {
        GrowthBook subject = growthBook();

        assertEquals("light", subject.getStringFeature(TypedKey.ofString("missing"), "light"));
        assertEquals(Integer.valueOf(10), subject.getIntegerFeature(TypedKey.ofInteger("missing"), 10));
        assertEquals(Double.valueOf(9.9), subject.getDoubleFeature(TypedKey.ofDouble("missing"), 9.9));
        assertEquals(Float.valueOf(9.9f), subject.getFloatFeature(TypedKey.ofFloat("missing"), 9.9f));
    }

    @Test
    @DisplayName("getFeatureValue infers the deserialization class from the key")
    void getFeatureValue_infersClassFromKey() {
        GrowthBook subject = growthBook();

        assertEquals("dark", subject.getFeatureValue(Features.THEME, "light"));
        assertEquals(Integer.valueOf(25), subject.getFeatureValue(Features.MAX_ITEMS, 0));
    }

    static final class Features {

        static final TypedKey<String> THEME = TypedKey.ofString("theme");
        static final TypedKey<Double> RATIO = TypedKey.ofDouble("ratio");
        static final TypedKey<Float> WEIGHT = TypedKey.ofFloat("weight");
        static final TypedKey<Boolean> NEW_HOME = TypedKey.ofBoolean("new-home");
        static final TypedKey<Boolean> DARK_MODE = TypedKey.ofBoolean("dark-mode");
        static final TypedKey<Integer> MAX_ITEMS = TypedKey.ofInteger("max-items");
        static final TypedKey<HomeConfig> HOME_CONFIG = TypedKey.of("home-config", HomeConfig.class);

        private Features() {
        }
    }

    static final class HomeConfig {
        private final String title;
        private final int limit;

        HomeConfig(String title, int limit) {
            this.title = title;
            this.limit = limit;
        }

        String getTitle() {
            return title;
        }

        int getLimit() {
            return limit;
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
            return limit == other.limit && java.util.Objects.equals(title, other.title);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(title, limit);
        }
    }

    private static final String FEATURES_JSON = "{"
        + "\"new-home\":{\"defaultValue\":true},"
        + "\"dark-mode\":{\"defaultValue\":false},"
        + "\"theme\":{\"defaultValue\":\"dark\"},"
        + "\"max-items\":{\"defaultValue\":25},"
        + "\"ratio\":{\"defaultValue\":1.5},"
        + "\"weight\":{\"defaultValue\":2.5},"
        + "\"home-config\":{\"defaultValue\":{\"title\":\"Welcome\",\"limit\":5}}"
        + "}";

    private GrowthBook growthBook() {
        GBContext context = GBContext.builder()
            .featuresJson(FEATURES_JSON)
            .build();
        return new GrowthBook(context);
    }
}
