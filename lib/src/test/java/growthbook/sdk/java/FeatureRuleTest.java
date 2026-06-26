package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.model.FeatureRule;
import growthbook.sdk.java.model.Namespace;
import growthbook.sdk.java.model.OptionalField;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Type;
import java.util.ArrayList;

class FeatureRuleTest {

    @Test
    void canBeConstructed() {
        ArrayList<Float> weights = new ArrayList<>();
        weights.add(0.3f);
        weights.add(0.7f);

        ArrayList<Integer> variations = new ArrayList<>();

        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        FeatureRule<Integer> subject = new FeatureRule<>(
                null,
                "my-key",
                0.5f,
                new OptionalField<>(true, 100),
                variations,
                weights,
                namespace,
                "_id",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(0.5f, subject.getCoverage());
        assertEquals(0.5f, subject.getCoverage());
        assertEquals(100, subject.getForce() != null ? subject.getForce().getValue() : null);
        assertEquals(100, subject.getForce() != null ? subject.getForce().getValue() : null);
        assertEquals(namespace, subject.getNamespace());
        assertEquals(namespace, subject.getNamespace());
        assertEquals("_id", subject.getHashAttribute());
        assertEquals("_id", subject.getHashAttribute());
        assertEquals(weights, subject.getWeights());
        assertEquals(weights, subject.getWeights());
    }

    @Test
    void canBeBuilt() {
        ArrayList<Float> weights = new ArrayList<>();
        weights.add(0.3f);
        weights.add(0.7f);

        ArrayList<String> variations = new ArrayList<>();

        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        FeatureRule<String> subject = FeatureRule
                .<String>builder()
                .coverage(0.5f)
                .force(new OptionalField<>(true, "forced-value"))
                .namespace(namespace)
                .weights(weights)
                .variations(variations)
                .hashAttribute("_id")
                .build();

        assertEquals(0.5f, subject.getCoverage());
        assertEquals(0.5f, subject.getCoverage());
        assertEquals("forced-value", subject.getForce() != null ? subject.getForce().getValue() : null);
        assertEquals("forced-value", subject.getForce() != null ? subject.getForce().getValue() : null);
        assertEquals(namespace, subject.getNamespace());
        assertEquals(namespace, subject.getNamespace());
        assertEquals(weights, subject.getWeights());
        assertEquals(weights, subject.getWeights());
        assertEquals("_id", subject.getHashAttribute());
        assertEquals("_id", subject.getHashAttribute());
    }

    @Test
    void defaultBuilderValues() {
        FeatureRule<String> subject = FeatureRule
                .<String>builder()
                .build();

        assertNull(subject.getCoverage());
        assertNull(subject.getForce());
        assertNull(subject.getNamespace());
        assertNull(subject.getWeights());
        assertEquals("id", subject.getHashAttribute());
        assertEquals("id", subject.getHashAttribute());
    }

    @Test
    @DisplayName("Deserializes custom fields on experiment feature rules")
    void deserializesCustomFields() {
        Type featureRuleType = new TypeToken<FeatureRule<Boolean>>() {}.getType();

        FeatureRule<Boolean> subject = GrowthBookJsonUtils.getInstance().gson.fromJson("{\"key\":\"experiment-key\",\"variations\":[false,true],\"customFields\":{\"cfl_ticket\":\"APX-123\",\"cfl_priority\":2,\"cfl_reviewed\":true}}", featureRuleType);

        assertNotNull(subject.getCustomFields());
        assertEquals("APX-123", subject.getCustomFields().get("cfl_ticket"));
        assertEquals(2L, subject.getCustomFields().get("cfl_priority"));
        assertEquals(Boolean.TRUE, subject.getCustomFields().get("cfl_reviewed"));
    }
}
