package growthbook.sdk.java.model;

import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentResultTest {

    private static final String JSON = "{\"value\":\"c\",\"variationId\":2,\"inExperiment\":true,\"hashAttribute\":\"id\","
            + "\"hashValue\":\"user-1\",\"featureId\":\"feature-1\",\"hashUsed\":true,\"key\":\"2\",\"passthrough\":true}";

    @Test
    @DisplayName("The builder and the constructor produce equal results and derive the key from the variation id")
    void builderAndConstructorProduceEqualResults() {
        // Given
        ExperimentResult<String> built = ExperimentResult.<String>builder()
                .value("c")
                .variationId(2)
                .inExperiment(true)
                .hashAttribute("id")
                .hashValue("user-1")
                .featureId("feature-1")
                .hashUsed(true)
                .build();

        // When
        ExperimentResult<String> constructed = new ExperimentResult<>(
                "c", 2, true, "id", "user-1", "feature-1", true, null, null, null, null, null);

        // Then
        assertEquals("c", built.getValue());
        assertEquals(2, built.getVariationId());
        assertTrue(built.getInExperiment());
        assertEquals("id", built.getHashAttribute());
        assertEquals("user-1", built.getHashValue());
        assertEquals("feature-1", built.getFeatureId());
        assertTrue(built.getHashUsed());
        assertEquals("2", built.getKey());
        assertEquals(built, constructed);
        assertEquals(built.hashCode(), constructed.hashCode());
    }

    @Test
    @DisplayName("Defaults to the id hash attribute and not in the experiment")
    void appliesDefaultsForMissingValues() {
        // When
        ExperimentResult<String> subject = ExperimentResult.<String>builder().build();

        // Then
        assertNull(subject.getValue());
        assertNull(subject.getHashValue());
        assertEquals("id", subject.getHashAttribute());
        assertNull(subject.getVariationId());
        assertNull(subject.getFeatureId());
        assertNull(subject.getKey());
        assertFalse(subject.getInExperiment());
        assertFalse(subject.getHashUsed());
    }

    @Test
    @DisplayName("Serializes passThrough as the passthrough JSON field")
    void serializesToJson() {
        // Given
        ExperimentResult<String> subject = ExperimentResult.<String>builder()
                .value("c")
                .variationId(2)
                .inExperiment(true)
                .hashAttribute("id")
                .hashValue("user-1")
                .featureId("feature-1")
                .hashUsed(true)
                .passThrough(true)
                .build();

        // When
        String json = subject.toJson();

        // Then
        assertEquals(JSON, json);
        assertEquals(JSON, GrowthBookJsonUtils.getInstance().gson.toJson(subject));
    }

    @Test
    @DisplayName("Deserializes every field from JSON")
    void deserializesFromJson() {
        // Given
        Type type = new TypeToken<ExperimentResult<String>>() {}.getType();

        // When
        ExperimentResult<String> subject = GrowthBookJsonUtils.getInstance().gson.fromJson(JSON, type);

        // Then
        assertEquals("c", subject.getValue());
        assertEquals(2, subject.getVariationId());
        assertTrue(subject.getInExperiment());
        assertEquals("id", subject.getHashAttribute());
        assertEquals("user-1", subject.getHashValue());
        assertEquals("feature-1", subject.getFeatureId());
        assertTrue(subject.getHashUsed());
        assertEquals("2", subject.getKey());
        assertTrue(subject.getPassThrough());
    }
}
