package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentTest {

    @Test
    void canBeConstructed() {
        ArrayList<Float> weights = new ArrayList<Float>();
        weights.add(0.3f);
        weights.add(0.7f);

        Experiment experiment = new Experiment(
                "my_experiment",
                weights,
                true,
                0.5f,
                0.2f,
                "_id"
        );
        assertEquals(0.5f, experiment.coverage);
        assertEquals(0.5f, experiment.getCoverage());
        assertEquals(0.2f, experiment.force);
        assertEquals(0.2f, experiment.getForce());
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
        ArrayList<Float> weights = new ArrayList<Float>();
        weights.add(0.3f);
        weights.add(0.7f);

        Experiment experiment = Experiment
                .builder()
                .coverage(0.5f)
                .force(0.2f)
                .weights(weights)
                .isActive(true)
                .key("my_experiment")
                .hashAttribute("_id")
                .build();

        assertEquals(0.5f, experiment.coverage);
        assertEquals(0.5f, experiment.getCoverage());
        assertEquals(0.2f, experiment.force);
        assertEquals(0.2f, experiment.getForce());
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
        Experiment experiment = Experiment
                .builder()
                .build();

        assertNull(experiment.coverage);
        assertNull(experiment.force);
        assertNull(experiment.key);
        assertNull(experiment.isActive);
        assertEquals("id", experiment.hashAttribute);
        assertEquals("id", experiment.getHashAttribute());
    }
}