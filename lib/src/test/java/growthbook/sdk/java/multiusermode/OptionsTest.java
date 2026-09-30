package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.multiusermode.configurations.Options;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
}
