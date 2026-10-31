package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.multiusermode.configurations.Options;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsTest {
    @Test
    void normalizesGlobalForcedVariationsFromExternalNumericMap() {
        Map<String, Object> forcedVariations = new HashMap<>();
        forcedVariations.put("integer", 1);
        forcedVariations.put("double", 1.0);
        forcedVariations.put("invalid", true);

        Options options = Options.builder()
                .globalForcedVariationsMap(forcedVariations)
                .build();

        assertEquals(Integer.valueOf(1), options.getGlobalForcedVariationsMap().get("integer"));
        assertEquals(Integer.valueOf(1), options.getGlobalForcedVariationsMap().get("double"));
        assertFalse(options.getGlobalForcedVariationsMap().containsKey("invalid"));
    }

    @Test
    void supports0110PositionalConstructor_defaultsSseReconnectOn() {
        Options options = new Options(
                null,
                false,
                null,
                false,
                null,
                "https://cdn.growthbook.io",
                "sdk-123",
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
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertTrue(options.isSseReconnectOnFailure());
    }

    @Test
    void publicConstructorSurfaceKeepsEveryPreviouslyAvailableArity() {
        // Positional constructors are part of the published API: dropping one is a binary break
        // (NoSuchMethodError) that compiles cleanly here. 21 and 28 shipped in 0.11.0, 29 exists
        // on main, and 30 is the current full signature. New parameters must be added alongside
        // a delegating overload, never by widening an existing signature in place.
        Set<Integer> arities = new HashSet<>();
        for (Constructor<?> constructor : Options.class.getConstructors()) {
            arities.add(constructor.getParameterCount());
        }

        assertTrue(arities.containsAll(Arrays.asList(21, 28, 29, 30)),
                "missing positional Options constructors, found arities: " + arities);
    }
}
