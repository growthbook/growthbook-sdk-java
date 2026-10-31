package growthbook.sdk.java.model;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureRuleTest {

    @Test
    @DisplayName("The builder and the constructor keep the given values")
    @SuppressWarnings("deprecation")
    void builderAndConstructorKeepGivenValues() {
        // Given
        ArrayList<Double> weights = new ArrayList<>(Arrays.asList(0.3, 0.7));
        Namespace namespace = Namespace.builder().id("namespace-1").rangeStart(0.0).rangeEnd(0.6).build();

        // When
        FeatureRule<Integer> built = FeatureRule.<Integer>builder()
                .key("rule-key")
                .coverage(0.5)
                .force(new OptionalField<>(true, 100))
                .namespace(namespace)
                .weights(weights)
                .variations(new ArrayList<Integer>())
                .hashAttribute("_id")
                .build();
        FeatureRule<Integer> constructed = new FeatureRule<>(
                null, "rule-key", 0.5, new OptionalField<>(true, 100), new ArrayList<Integer>(), weights, namespace, "_id",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Then
        for (FeatureRule<Integer> subject : Arrays.asList(built, constructed)) {
            assertEquals("rule-key", subject.getKey());
            assertEquals(0.5, subject.getCoverage());
            assertTrue(subject.getForce().isPresent());
            assertEquals(100, subject.getForce().getValue());
            assertEquals(namespace, subject.getNamespace());
            assertEquals(Arrays.asList(0.3, 0.7), subject.getWeights());
            assertEquals(new ArrayList<Integer>(), subject.getVariations());
            assertEquals("_id", subject.getHashAttribute());
            assertNull(subject.getCustomFields());
        }
    }

    @Test
    @DisplayName("The builder defaults to an empty id and the id hash attribute")
    @SuppressWarnings("deprecation")
    void appliesBuilderDefaults() {
        // When
        FeatureRule<String> subject = FeatureRule.<String>builder().build();

        // Then
        assertEquals("", subject.getId());
        assertNull(subject.getKey());
        assertNull(subject.getCoverage());
        assertNull(subject.getForce());
        assertNull(subject.getNamespace());
        assertNull(subject.getWeights());
        assertEquals("id", subject.getHashAttribute());
    }

    @Test
    @DisplayName("Deserializes custom fields on experiment rules")
    void deserializesCustomFields() {
        // Given
        Type type = new TypeToken<FeatureRule<Boolean>>() {}.getType();
        String json = "{\"key\":\"experiment-key\",\"variations\":[false,true],"
                + "\"customFields\":{\"cfl_ticket\":\"TICKET-1\",\"cfl_priority\":2,\"cfl_reviewed\":true}}";

        // When
        FeatureRule<Boolean> subject = GrowthBookJsonUtils.getInstance().gson.fromJson(json, type);

        // Then
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("cfl_ticket", "TICKET-1");
        expected.put("cfl_priority", 2L);
        expected.put("cfl_reviewed", true);
        assertEquals(expected, subject.getCustomFields());
    }
}
