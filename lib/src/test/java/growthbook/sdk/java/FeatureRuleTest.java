package growthbook.sdk.java;

import growthbook.sdk.java.models.Namespace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class FeatureRuleTest {

    @Test
    void canBeConstructed() {
        ArrayList<Float> weights = new ArrayList<Float>();
        weights.add(0.3f);
        weights.add(0.7f);

        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        FeatureRule<String> subject = new FeatureRule<String>(
                0.5f,
                "forced-value",
                weights,
                namespace,
                "_id"
        );

        assertEquals(0.5f, subject.coverage);
        assertEquals(0.5f, subject.getCoverage());
        assertEquals("forced-value", subject.force);
        assertEquals("forced-value", subject.getForce());
        assertEquals(namespace, subject.namespace);
        assertEquals(namespace, subject.getNamespace());
        assertEquals("_id", subject.hashAttribute);
        assertEquals("_id", subject.getHashAttribute());
        assertEquals(weights, subject.weights);
        assertEquals(weights, subject.getWeights());
    }

    @Test
    void canBeBuilt() {
        ArrayList<Float> weights = new ArrayList<Float>();
        weights.add(0.3f);
        weights.add(0.7f);

        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        FeatureRule<String> subject = FeatureRule
                .<String>builder()
                .coverage(0.5f)
                .force("forced-value")
                .namespace(namespace)
                .weights(weights)
                .hashAttribute("_id")
                .build();

        assertEquals(0.5f, subject.coverage);
        assertEquals(0.5f, subject.getCoverage());
        assertEquals("forced-value", subject.force);
        assertEquals("forced-value", subject.getForce());
        assertEquals(namespace, subject.namespace);
        assertEquals(namespace, subject.getNamespace());
        assertEquals(weights, subject.weights);
        assertEquals(weights, subject.getWeights());
        assertEquals("_id", subject.hashAttribute);
        assertEquals("_id", subject.getHashAttribute());
    }

    @Test
    void defaultBuilderValues() {
        FeatureRule<String> subject = FeatureRule
                .<String>builder()
                .build();

        assertNull(subject.coverage);
        assertNull(subject.force);
        assertNull(subject.namespace);
        assertNull(subject.weights);
        assertEquals("id", subject.hashAttribute);
        assertEquals("id", subject.getHashAttribute());
    }
}
