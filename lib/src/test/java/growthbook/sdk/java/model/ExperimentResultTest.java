package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Type;

class ExperimentResultTest {
    final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    @Test
    void test_canBeBuilt() {
        ExperimentResult<String> subject = ExperimentResult
                .<String>builder()
                .value("c")
                .variationId(2)
                .inExperiment(true)
                .hashAttribute("id")
                .hashValue("123")
                .featureId("my_feature")
                .hashUsed(true)
                .build();

        assertEquals("c", subject.getValue());
        assertEquals(2, subject.getVariationId());
        assertEquals(true, subject.getInExperiment());
        assertEquals("id", subject.getHashAttribute());
        assertEquals("123", subject.getHashValue());
        assertEquals("my_feature", subject.getFeatureId());
        assertEquals(true, subject.getHashUsed());
    }

    @Test
    void test_builderDefaultValues() {
        ExperimentResult<String> subject = ExperimentResult
                .<String>builder()
                .build();

        // null
        assertNull(subject.getValue());
        assertNull(subject.getHashValue());
        assertEquals("id", subject.getHashAttribute());
        assertNull(subject.getVariationId());
        assertNull(subject.getFeatureId());
        // false
        assertFalse(subject.getInExperiment());
        assertFalse(subject.getHashUsed());
    }

    @Test
    void test_canBeConstructed() {
        ExperimentResult<String> subject = new ExperimentResult<>(
                "c",
                2,
                true,
                "id",
                "123",
                "my_feature",
                true,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("c", subject.getValue());
        assertEquals(2, subject.getVariationId());
        assertEquals(true, subject.getInExperiment());
        assertEquals("id", subject.getHashAttribute());
        assertEquals("123", subject.getHashValue());
        assertEquals("my_feature", subject.getFeatureId());
        assertEquals(true, subject.getHashUsed());
    }

    @Test
    void test_canBeSerialized() {
        ExperimentResult<String> subject = ExperimentResult
                .<String>builder()
                .value("c")
                .variationId(2)
                .inExperiment(true)
                .hashAttribute("id")
                .hashValue("123")
                .featureId("my_feature")
                .hashUsed(true)
                .passThrough(true)
                .build();

        assertEquals(
                "{\"value\":\"c\",\"variationId\":2,\"inExperiment\":true,\"hashAttribute\":\"id\",\"hashValue\":\"123\",\"featureId\":\"my_feature\",\"hashUsed\":true,\"key\":\"2\",\"passthrough\":true}",
                subject.toJson()
        );
        assertEquals(
                "{\"value\":\"c\",\"variationId\":2,\"inExperiment\":true,\"hashAttribute\":\"id\",\"hashValue\":\"123\",\"featureId\":\"my_feature\",\"hashUsed\":true,\"key\":\"2\",\"passthrough\":true}",
                jsonUtils.gson.toJson(subject)
        );
    }

    @Test
    void test_canBeDeserialized() {
        Type experimentResultType = new TypeToken<ExperimentResult<String>>() {}.getType();
        ExperimentResult<String> subject = jsonUtils.gson.fromJson("{\"featureId\":\"my_feature\",\"value\":\"c\",\"variationId\":2,\"inExperiment\":true,\"hashUsed\":true,\"hashAttribute\":\"id\",\"hashValue\":\"123\"}", experimentResultType);

        assertEquals("c", subject.getValue());
        assertEquals(2, subject.getVariationId());
        assertEquals(true, subject.getInExperiment());
        assertEquals("id", subject.getHashAttribute());
        assertEquals("123", subject.getHashValue());
        assertEquals("my_feature", subject.getFeatureId());
        assertEquals(true, subject.getHashUsed());
    }

    @Test
    void test_canBeComparedForEquality() {
        ExperimentResult<String> a = ExperimentResult
                .<String>builder()
                .value("c")
                .variationId(2)
                .inExperiment(true)
                .hashAttribute("id")
                .hashValue("123")
                .featureId("my_feature")
                .hashUsed(true)
                .build();

        ExperimentResult<String> b = ExperimentResult
                .<String>builder()
                .value("c")
                .variationId(2)
                .inExperiment(true)
                .hashAttribute("id")
                .hashValue("123")
                .featureId("my_feature")
                .hashUsed(true)
                .build();

        assertEquals(a, b);
    }
}
