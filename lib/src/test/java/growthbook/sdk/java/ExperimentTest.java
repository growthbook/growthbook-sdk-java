package growthbook.sdk.java;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentTest {

    final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

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
        assertNull(experiment.isActive);
        assertEquals("id", experiment.hashAttribute);
        assertEquals("id", experiment.getHashAttribute());
    }

    @Test
    void test_canBeSerialized() {
        ArrayList<Float> weights = new ArrayList<>();
        weights.add(0.3f);
        weights.add(0.7f);

        ArrayList<Integer> variations = new ArrayList<>();
        variations.add(100);
        variations.add(200);

        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(1.0f)
                .build();

        Experiment<Integer> subject = Experiment
                .<Integer>builder()
                .key("my_experiment")
                .variations(variations)
                .weights(weights)
                .isActive(true)
                .coverage(0.5f)
                .conditionJson("{}")
                .namespace(namespace)
                .force(1)
                .hashAttribute("_id")
                .build();

        assertEquals("{\"key\":\"my_experiment\",\"variations\":[100,200],\"weights\":[0.3,0.7],\"isActive\":true,\"coverage\":0.5,\"namespace\":[\"pricing\",0.0,1.0],\"force\":1,\"hashAttribute\":\"_id\"}", subject.toJson());
    }

    @Test
    void test_canBeDeserialized() {
        Type experimentType = new TypeToken<Experiment<Integer>>() {}.getType();

        Experiment<Integer> subject = jsonUtils.gson.fromJson("{\"key\":\"my_experiment\",\"variations\":[100,200],\"weights\":[0.3,0.7],\"isActive\":true,\"coverage\":0.5,\"namespace\":[\"pricing\",0.0,1.0],\"force\":1,\"hashAttribute\":\"_id\"}", experimentType);

        assertEquals("pricing", subject.getNamespace().getId());
        assertEquals(100, subject.getVariations().get(0));
        assertEquals(200, subject.getVariations().get(1));
    }
}
