package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;


import com.google.gson.reflect.TypeToken;

import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

class FeatureResultTest {
    private static GrowthBookJsonUtils utils;
    @BeforeAll
    static void beforeAll() {
        utils = GrowthBookJsonUtils.getInstance();

    }

    @Test
    void canBeConstructed() {
        FeatureResult subject = new FeatureResult(
                100,
                FeatureResultSource.DEFAULT_VALUE,
                null,
                null,
                "my-rule-id"
        );

        assertNull(subject.getExperiment());
        assertEquals(100, subject.getValue());
    }

    @Test
    void canBeBuilt() {
        FeatureResult<String> subject = FeatureResult
                .<String>builder()
                .value("hello")
                .experiment(null)
                .experimentResult(null)
                .ruleId("my-rule-id")
                .source(FeatureResultSource.EXPERIMENT)
                .build();

        assertNull(subject.getExperiment());
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

    // region isOn() isOff()

    @Test
    void featureResult_isOn_withNonZeroValue_returnsTrue_forIntegers() {
        FeatureResult<Integer> subject = FeatureResult
                .<Integer>builder()
                .value(1)
                .source(FeatureResultSource.FORCE)
                .build();

        assertTrue(subject.isOn());
        assertFalse(subject.isOff());
    }

    @Test
    void featureResult_isOn_withZeroValue_returnsFalse_forIntegers() {
        FeatureResult<Integer> subject = FeatureResult
                .<Integer>builder()
                .value(0)
                .source(FeatureResultSource.FORCE)
                .build();

        assertFalse(subject.isOn());
        assertTrue(subject.isOff());
    }

    // floats

    @Test
    void featureResult_isOn_withNonZeroValue_returnsTrue_forFloats() {
        FeatureResult<Float> subject = FeatureResult
                .<Float>builder()
                .value(1.0f)
                .source(FeatureResultSource.FORCE)
                .build();

        assertTrue(subject.isOn());
        assertFalse(subject.isOff());
    }

    @Test
    void featureResult_isOn_withZeroValue_returnsFalse_forFloats() {
        FeatureResult<Float> subject = FeatureResult
                .<Float>builder()
                .value(0.0f)
                .source(FeatureResultSource.FORCE)
                .build();

        assertFalse(subject.isOn());
        assertTrue(subject.isOff());
    }

    // doubles

    @Test
    void featureResult_isOn_withNonZeroValue_returnsTrue_forDoubles() {
        FeatureResult<Double> subject = FeatureResult
                .<Double>builder()
                .value(1.0)
                .source(FeatureResultSource.FORCE)
                .build();

        assertTrue(subject.isOn());
        assertFalse(subject.isOff());
    }

    @Test
    void featureResult_isOn_withZeroValue_returnsFalse_forDoubles() {
        FeatureResult<Double> subject = FeatureResult
                .<Double>builder()
                .value(0)
                .source(FeatureResultSource.FORCE)
                .build();

        assertFalse(subject.isOn());
        assertTrue(subject.isOff());
    }

    // strings

    @Test
    void featureResult_isOn_withNonEmptyValue_returnsTrue_forStrings() {
        FeatureResult<String> subject = FeatureResult
                .<String>builder()
                .value("hello, world!")
                .source(FeatureResultSource.FORCE)
                .build();

        assertTrue(subject.isOn());
        assertFalse(subject.isOff());
    }

    @Test
    void featureResult_isOn_withEmptyValue_returnsFalse_forStrings() {
        FeatureResult<String> subject = FeatureResult
                .<String>builder()
                .value("")
                .source(FeatureResultSource.FORCE)
                .build();

        assertFalse(subject.isOn());
        assertTrue(subject.isOff());
    }

    @Test
    void featureResult_isOn_withPerceivedNotEmptyCollection() {
        FeatureResult<Object> subject = FeatureResult
                .builder()
                .value(Collections.singleton("acme"))
                .build();

        assertTrue(subject.isOn());
        assertFalse(subject.isOff());
    }

    @Test
    void featureResult_isOff_withPerceivedEmptyCollection() {
        FeatureResult<Object> subject = FeatureResult
                .builder()
                .value(Collections.emptyList())
                .build();

        assertFalse(subject.isOn());
        assertTrue(subject.isOff());
    }

    @Test
    void proofOfConceptCollectionTest() throws NoSuchFieldException, IllegalAccessException {
        String featuresJsonString =  "{\"test\":{\"defaultValue\":[],\"rules\":[{\"force\":[\"line1\",\"line2\"]}]}}";
        GBContext ctx = GBContext.builder()
            .featuresJson(featuresJsonString)
            .build();

        GBFeaturesRepository featuresRepository = new GBFeaturesRepository(
                "https://cdn.growthbook.io",
                "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                null,
                null,
                null,
                null,
                true,
                null,
                null
        );

        Type featureMapType = new TypeToken<Map<String, Feature<?>>>() {}.getType();
        Map<String, Feature<?>> featuresMap = utils.gson.fromJson(featuresJsonString, featureMapType);

        // Встановлюємо Map у приватне поле parsedFeatures через reflection
        Field parsedFeaturesField = GBFeaturesRepository.class.getDeclaredField("parsedFeatures");
        parsedFeaturesField.setAccessible(true);
        parsedFeaturesField.set(featuresRepository, featuresMap);


        GrowthBook growthBook = new GrowthBook(ctx, featuresRepository);

        String featureName = "test";

        assertTrue(growthBook.isOn(featureName));
        assertFalse(growthBook.isOff(featureName));

        Object value = growthBook.getFeatureValue(featureName, new ArrayList<>());

        assertInstanceOf(Collection.class, value);
        assertFalse(((Collection<?>) value).isEmpty());
    }

    // endregion isOn() isOff()
}
