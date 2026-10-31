package growthbook.sdk.java.multiusermode.configurations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptionsTest {

    @Test
    @DisplayName("Normalizes global forced variations to integers and drops non-numeric values")
    void normalizesGlobalForcedVariations() {
        // Given
        Map<String, Object> forcedVariations = new HashMap<>();
        forcedVariations.put("integer", 1);
        forcedVariations.put("double", 1.0);
        forcedVariations.put("invalid", true);

        // When
        Options options = Options.builder().globalForcedVariationsMap(forcedVariations).build();

        // Then
        Map<String, Integer> expected = new HashMap<>();
        expected.put("integer", 1);
        expected.put("double", 1);
        assertEquals(expected, options.getGlobalForcedVariationsMap());
    }

    @Test
    @DisplayName("The 0.11.0 positional constructor turns SSE reconnect on by default")
    void positionalConstructorFrom0110TurnsSseReconnectOn() {
        // When
        Options options = new Options(
                null, false, null, false, null, "https://cdn.growthbook.io", "sdk-123",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        // Then
        assertTrue(options.isSseReconnectOnFailure());
    }

    @Test
    @DisplayName("Keeps every previously published positional constructor arity")
    void keepsEveryPublishedConstructorArity() {
        // Given
        Set<Integer> arities = new TreeSet<>();
        for (Constructor<?> constructor : Options.class.getConstructors()) {
            arities.add(constructor.getParameterCount());
        }

        // When
        boolean allPresent = arities.containsAll(Arrays.asList(21, 28, 29, 30));

        // Then
        assertTrue(allPresent, "found arities: " + arities);
    }
}
