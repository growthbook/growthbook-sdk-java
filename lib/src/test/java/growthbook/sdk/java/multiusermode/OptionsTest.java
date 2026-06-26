package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.multiusermode.configurations.Options;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
