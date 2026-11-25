package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExperimentHelperTest {

    @Test
    @DisplayName("Reports an assignment as tracked only after it has been seen once")
    @SuppressWarnings("deprecation")
    void reportsAssignmentTrackedAfterFirstSighting() {
        // Given
        growthbook.sdk.java.util.ExperimentHelper helper = new growthbook.sdk.java.util.ExperimentHelper();
        Experiment<String> experiment = experiment("exp-1");

        // When
        boolean first = helper.isTracked(experiment, result("id", "user-1", 1));
        boolean second = helper.isTracked(experiment, result("id", "user-1", 1));

        // Then
        assertFalse(first);
        assertTrue(second);
    }

    @Test
    @DisplayName("Tracks each user, experiment and variation separately")
    @SuppressWarnings("deprecation")
    void tracksUserExperimentAndVariationSeparately() {
        // Given
        growthbook.sdk.java.util.ExperimentHelper helper = new growthbook.sdk.java.util.ExperimentHelper();
        helper.isTracked(experiment("exp-1"), result("id", "user-1", 0));

        // When
        boolean otherVariation = helper.isTracked(experiment("exp-1"), result("id", "user-1", 1));
        boolean otherUser = helper.isTracked(experiment("exp-1"), result("id", "user-2", 0));
        boolean otherExperiment = helper.isTracked(experiment("exp-2"), result("id", "user-1", 0));

        // Then
        assertFalse(otherVariation);
        assertFalse(otherUser);
        assertFalse(otherExperiment);
    }

    @Test
    @DisplayName("Tracks a result without a hash attribute or hash value")
    @SuppressWarnings("deprecation")
    void tracksResultWithoutHashAttributeOrValue() {
        // Given
        growthbook.sdk.java.util.ExperimentHelper helper = new growthbook.sdk.java.util.ExperimentHelper();
        ExperimentResult<String> anonymous = ExperimentResult.<String>builder().variationId(0).build();

        // When
        boolean first = helper.isTracked(experiment("exp-1"), anonymous);
        boolean second = helper.isTracked(experiment("exp-1"), anonymous);

        // Then
        assertFalse(first);
        assertTrue(second);
    }

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder().key(key).build();
    }

    private static ExperimentResult<String> result(String hashAttribute, String hashValue, int variationId) {
        return ExperimentResult.<String>builder()
                .hashAttribute(hashAttribute)
                .hashValue(hashValue)
                .variationId(variationId)
                .build();
    }
}
