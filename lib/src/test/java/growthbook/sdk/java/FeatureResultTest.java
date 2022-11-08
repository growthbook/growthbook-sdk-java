package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeatureResultTest {
    @Test
    void canBeConstructed() {
        FeatureResult subject = new FeatureResult(
                100,
                FeatureResultSource.DEFAULT_VALUE,
                null,
                null,
                "my-rule-id"
        );

        assertNull(subject.experiment);
        assertEquals(100, subject.getValue());
    }

    @Test
    void canBeBuilt() {
        FeatureResult subject = FeatureResult
                .<String>builder()
                .value("hello")
                .experiment(null)
                .experimentResult(null)
                .ruleId("my-rule-id")
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        assertNull(subject.experiment);
        assertEquals("hello", subject.getValue());
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
        FeatureResult subject = FeatureResult
                .builder()
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        // experiment
        assertEquals("{\"on\":false,\"off\":true,\"source\":\"experiment\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // Unknown
        subject.setSource(FeatureResultSource.UNKNOWN_FEATURE);
        assertEquals("{\"on\":false,\"off\":true,\"source\":\"unknownFeature\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // force
        subject.setSource(FeatureResultSource.FORCE);
        assertEquals("{\"on\":false,\"off\":true,\"source\":\"force\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // defaultValue
        subject.setSource(FeatureResultSource.DEFAULT_VALUE);
        assertEquals("{\"on\":false,\"off\":true,\"source\":\"defaultValue\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
    }
}
