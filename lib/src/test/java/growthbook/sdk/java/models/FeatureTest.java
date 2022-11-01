package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureTest {

    @Test
    void test_simpleStringRule() {
        Feature subject = new Feature("{\"defaultValue\": \"Hello!\"}");

        assertEquals("Hello!", subject.getDefaultValue());
        assertTrue(subject.getRules().isEmpty());
    }

    @Test
    void test_canDeserializeRules() {
        Feature<Boolean> subject = new Feature<Boolean>("{ \"defaultValue\": false, \"rules\": [ { \"variations\": [ false, true ], \"coverage\": 1, \"weights\": [ 0.5, 0.5 ], \"key\": \"purchase_cta\", \"hashAttribute\": \"id\" } ] }");

        assertEquals(1, subject.getRules().size());
        assertEquals(2, subject.getRules().get(0).getVariations().size());
        assertEquals(2, subject.getRules().get(0).getWeights().size());
        assertEquals("purchase_cta", subject.getRules().get(0).getKey());
    }
}
