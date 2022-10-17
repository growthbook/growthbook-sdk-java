package growthbook.sdk.java.models;

import growthbook.sdk.java.services.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureResultTest {
    @Test
    void canBeConstructed() {
        FeatureResult<Integer> subject = new FeatureResult<Integer>(
                100,
                FeatureResultSource.DEFAULT_VALUE,
                null
        );

        assertNull(subject.experiment);
        assertEquals(100, subject.value);
    }

    @Test
    void canBeBuilt() {
        FeatureResult<String> subject = FeatureResult
                .<String>builder()
                .value("hello")
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        assertNull(subject.experiment);
        assertEquals("hello", subject.value);
    }

    @Test
    void canBeSerializedToJson() {
        FeatureResult<String> subject = FeatureResult
                .<String>builder()
                .value("hello")
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        String result = GrowthBookJsonUtils.getInstance().gson.toJson(subject);

        assertEquals("{\"value\":\"hello\",\"source\":\"experiment\"}", result);
    }

    @Test
    void featureResultSourceOutputsCorrectlyToString() {
        assertEquals("unknownFeature", FeatureResultSource.UNKNOWN_FEATURE.toString());
        assertEquals("experiment", FeatureResultSource.EXPERIMENT.toString());
        assertEquals("force", FeatureResultSource.FORCE.toString());
        assertEquals("defaultValue", FeatureResultSource.DEFAULT_VALUE.toString());
    }

    @Test
    void featureResultSourceOutputsCorrectlyToJson() {
        FeatureResult<String> subject = FeatureResult
                .<String>builder()
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        // experiment
        assertEquals("{\"source\":\"experiment\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // Unknown
        subject.setSource(FeatureResultSource.UNKNOWN_FEATURE);
        assertEquals("{\"source\":\"unknownFeature\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // force
        subject.setSource(FeatureResultSource.FORCE);
        assertEquals("{\"source\":\"force\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // defaultValue
        subject.setSource(FeatureResultSource.DEFAULT_VALUE);
        assertEquals("{\"source\":\"defaultValue\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
    }
}
