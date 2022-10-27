package growthbook.sdk.java.models;

import growthbook.sdk.java.services.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
//                .rawJsonValue("\"hello\"")
                .experiment(null)
                .experimentResult(null)
                .ruleId("my-rule-id")
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        assertNull(subject.experiment);
        assertEquals("hello", subject.getValue());
    }
//
//    @Test
//    void canBeSerializedToJson() {
//        FeatureResult subject = FeatureResult
//                .builder()
//                .rawJsonValue("\"hello\"")
//                .source(FeatureResultSource.EXPERIMENT)
//                .build();
//
//        String result = GrowthBookJsonUtils.getInstance().gson.toJson(subject);
//
//        assertEquals("{\"on\":false,\"value\":\"\"hello\"\",\"source\":\"experiment\"}", result);
//    }

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
        assertEquals("{\"on\":false,\"source\":\"experiment\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // Unknown
        subject.setSource(FeatureResultSource.UNKNOWN_FEATURE);
        assertEquals("{\"on\":false,\"source\":\"unknownFeature\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // force
        subject.setSource(FeatureResultSource.FORCE);
        assertEquals("{\"on\":false,\"source\":\"force\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        // defaultValue
        subject.setSource(FeatureResultSource.DEFAULT_VALUE);
        assertEquals("{\"on\":false,\"source\":\"defaultValue\"}", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
    }
}
