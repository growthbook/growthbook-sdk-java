package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentTest {

    @Test
    void canBeConstructed() {
        ArrayList<Float> weights = new ArrayList<>();
        weights.add(0.3f);
        weights.add(0.7f);

        ArrayList<Float> variations = new ArrayList<>();

        Namespace namespace = Namespace.builder().build();
        String conditionJson = "{}";

        Experiment<Float> experiment = new Experiment<Float>(
                "my_experiment",
                variations,
                weights,
                true,
                0.5f,
                conditionJson,
                namespace,
                1,
                "_id"
        );
        assertEquals(0.5f, experiment.coverage);
        assertEquals(0.5f, experiment.getCoverage());
        assertEquals(1, experiment.force);
        assertEquals(1, experiment.getForce());
        assertEquals("my_experiment", experiment.key);
        assertEquals("my_experiment", experiment.getKey());
        assertEquals("_id", experiment.hashAttribute);
        assertEquals("_id", experiment.getHashAttribute());
        assertEquals(0.3f, experiment.weights.get(0));
        assertEquals(0.7f, experiment.weights.get(1));
        assertTrue(experiment.isActive);
        assertTrue(experiment.getIsActive());
    }

    @Test
    void canBeBuilt() {
        ArrayList<Float> weights = new ArrayList<>();
        weights.add(0.3f);
        weights.add(0.7f);

        Experiment<Integer> experiment = Experiment
                .<Integer>builder()
                .coverage(0.5f)
                .force(1)
                .weights(weights)
                .isActive(true)
                .key("my_experiment")
                .hashAttribute("_id")
                .build();

        assertEquals(0.5f, experiment.coverage);
        assertEquals(0.5f, experiment.getCoverage());
        assertEquals(1, experiment.force);
        assertEquals(1, experiment.getForce());
        assertEquals(0.3f, experiment.weights.get(0));
        assertEquals(0.7f, experiment.weights.get(1));
        assertEquals("my_experiment", experiment.key);
        assertEquals("my_experiment", experiment.getKey());
        assertEquals("_id", experiment.hashAttribute);
        assertEquals("_id", experiment.getHashAttribute());
        assertTrue(experiment.isActive);
        assertTrue(experiment.getIsActive());
    }

    @Test
    void hasDefaultBuilderArguments() {
        Experiment<String> experiment = Experiment
                .<String>builder()
                .build();

        assertNull(experiment.coverage);
        assertNull(experiment.force);
        assertNull(experiment.key);
        assertTrue(experiment.isActive);
        assertEquals("id", experiment.hashAttribute);
        assertEquals("id", experiment.getHashAttribute());
    }
}
