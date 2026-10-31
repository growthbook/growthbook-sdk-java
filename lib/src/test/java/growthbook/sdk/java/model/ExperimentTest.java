package growthbook.sdk.java.model;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentTest {

    private static final Type EXPERIMENT_TYPE = new TypeToken<Experiment<Integer>>() {}.getType();

    @Test
    @DisplayName("The builder and the constructor produce equal experiments")
    @SuppressWarnings("deprecation")
    void builderAndConstructorProduceEqualExperiments() {
        // Given
        ArrayList<Double> weights = new ArrayList<>(Arrays.asList(0.3, 0.7));
        Namespace namespace = Namespace.builder().id("namespace-1").rangeStart(0.0).rangeEnd(1.0).build();
        Experiment<Integer> built = Experiment.<Integer>builder()
                .key("experiment-1")
                .variations(new ArrayList<Integer>())
                .weights(weights)
                .isActive(true)
                .coverage(0.5)
                .conditionJson(new JsonObject())
                .namespace(namespace)
                .force(1)
                .hashAttribute("_id")
                .build();

        // When
        Experiment<Integer> constructed = new Experiment<>(
                "experiment-1", new ArrayList<Integer>(), weights, true, 0.5, new JsonObject(), null, namespace, 1, "_id",
                null, null, null, null, null, null, null, null, null, null, null);

        // Then
        assertEquals("experiment-1", constructed.getKey());
        assertEquals(Arrays.asList(0.3, 0.7), constructed.getWeights());
        assertTrue(constructed.getIsActive());
        assertEquals(0.5, constructed.getCoverage());
        assertEquals(namespace, constructed.getNamespace());
        assertEquals(1, constructed.getForce());
        assertEquals("_id", constructed.getHashAttribute());
        assertNull(constructed.getCustomFields());
        assertEquals(built, constructed);
    }

    @Test
    @DisplayName("Defaults to no variations and the id hash attribute")
    void appliesBuilderDefaults() {
        // When
        Experiment<String> subject = Experiment.<String>builder().build();

        // Then
        assertNull(subject.getKey());
        assertEquals(new ArrayList<String>(), subject.getVariations());
        assertNull(subject.getCoverage());
        assertNull(subject.getForce());
        assertNull(subject.getIsActive());
        assertEquals("id", subject.getHashAttribute());
    }

    @Test
    @DisplayName("Serializes isActive as active and the namespace as a JSON triple")
    @SuppressWarnings("deprecation")
    void serializesToJson() {
        // Given
        Experiment<Integer> subject = Experiment.<Integer>builder()
                .key("experiment-1")
                .variations(new ArrayList<>(Arrays.asList(100, 200)))
                .weights(new ArrayList<>(Arrays.asList(0.3, 0.7)))
                .isActive(true)
                .coverage(0.5)
                .conditionJson(new JsonObject())
                .namespace(Namespace.builder().id("namespace-1").rangeStart(0.0).rangeEnd(1.0).build())
                .force(1)
                .hashAttribute("_id")
                .build();

        // When
        String json = subject.toJson();

        // Then
        assertEquals("{\"key\":\"experiment-1\",\"variations\":[100,200],\"weights\":[0.3,0.7],\"active\":true,"
                + "\"coverage\":0.5,\"conditionJson\":{},\"namespace\":[\"namespace-1\",0.0,1.0],\"force\":1,"
                + "\"hashAttribute\":\"_id\"}", json);
        assertEquals(json, subject.toString());
    }

    @Test
    @DisplayName("Deserializes active, namespace and variations from JSON")
    @SuppressWarnings("deprecation")
    void deserializesFromJson() {
        // Given
        String json = "{\"key\":\"experiment-1\",\"variations\":[100,200],\"weights\":[0.3,0.7],\"active\":true,"
                + "\"coverage\":0.5,\"namespace\":[\"namespace-1\",0.0,1.0],\"force\":1,\"hashAttribute\":\"_id\"}";

        // When
        Experiment<Integer> subject = GrowthBookJsonUtils.getInstance().gson.fromJson(json, EXPERIMENT_TYPE);

        // Then
        assertEquals("experiment-1", subject.getKey());
        assertEquals(Arrays.asList(100, 200), subject.getVariations());
        assertEquals(Arrays.asList(0.3, 0.7), subject.getWeights());
        assertTrue(subject.getIsActive());
        assertEquals(0.5, subject.getCoverage());
        assertEquals(Namespace.builder().id("namespace-1").rangeStart(0.0).rangeEnd(1.0).build(), subject.getNamespace());
        assertEquals(1, subject.getForce());
        assertEquals("_id", subject.getHashAttribute());
    }

    @Test
    @DisplayName("Deserializes custom fields with mixed value types")
    void deserializesCustomFieldsWithMixedValueTypes() {
        // Given
        String json = "{\"key\":\"experiment-1\",\"variations\":[100,200],\"customFields\":{\"cfl_string\":\"text value\","
                + "\"cfl_number\":42,\"cfl_decimal\":99.99,\"cfl_bool\":true,\"cfl_null\":null}}";

        // When
        Experiment<Integer> subject = GrowthBookJsonUtils.getInstance().gson.fromJson(json, EXPERIMENT_TYPE);

        // Then
        assertTrue(subject.hasCustomField("cfl_string"));
        assertTrue(subject.hasCustomField("cfl_null"));
        assertFalse(subject.hasCustomField("cfl_missing"));
        assertEquals("text value", subject.getCustomField("cfl_string"));
        assertEquals(42L, subject.getCustomField("cfl_number"));
        assertEquals(99.99, subject.getCustomField("cfl_decimal"));
        assertEquals(Boolean.TRUE, subject.getCustomField("cfl_bool"));
        assertNull(subject.getCustomField("cfl_null"));
        assertEquals(Integer.valueOf(42), subject.getCustomField("cfl_number", Integer.class));
        assertEquals("42", subject.getCustomField("cfl_number", String.class));
        assertEquals(Boolean.TRUE, subject.getCustomField("cfl_bool", Boolean.class));
        assertNull(subject.getCustomField("cfl_string", Integer.class));
    }

    @Test
    @DisplayName("The typed custom field accessor coerces compatible types, including numeric strings")
    void typedCustomFieldAccessorCoercesCompatibleTypes() {
        // Given
        String json = "{\"key\":\"experiment-1\",\"variations\":[100,200],"
                + "\"customFields\":{\"cfl_numeric_string\":\"42\",\"cfl_number\":42}}";

        // When
        Experiment<Integer> subject = GrowthBookJsonUtils.getInstance().gson.fromJson(json, EXPERIMENT_TYPE);

        // Then
        assertEquals(Integer.valueOf(42), subject.getCustomField("cfl_numeric_string", Integer.class));
        assertEquals(Double.valueOf(42.0), subject.getCustomField("cfl_number", Double.class));
        assertEquals(Long.valueOf(42), subject.getCustomField("cfl_number", Long.class));
    }

    @Test
    @DisplayName("Keeps custom fields null when the payload omits them")
    void keepsCustomFieldsNullWhenOmitted() {
        // When
        Experiment<Integer> subject = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"key\":\"experiment-1\",\"variations\":[100,200]}", EXPERIMENT_TYPE);

        // Then
        assertNull(subject.getCustomFields());
        assertNull(subject.getCustomField("cfl_missing"));
        assertNull(subject.getCustomField("cfl_missing", String.class));
        assertFalse(subject.hasCustomField("cfl_missing"));
    }

    @Test
    @DisplayName("Custom fields survive a JSON round trip")
    void customFieldsSurviveJsonRoundTrip() {
        // Given
        Map<String, Object> customFields = new LinkedHashMap<>();
        customFields.put("cfl_string", "text value");
        customFields.put("cfl_number", 42);
        Experiment<Integer> subject = Experiment.<Integer>builder().key("experiment-1").customFields(customFields).build();

        // When
        Experiment<Integer> deserialized = GrowthBookJsonUtils.getInstance().gson.fromJson(subject.toJson(), EXPERIMENT_TYPE);

        // Then
        assertEquals("text value", deserialized.getCustomField("cfl_string"));
        assertEquals(42L, deserialized.getCustomField("cfl_number"));
    }
}
