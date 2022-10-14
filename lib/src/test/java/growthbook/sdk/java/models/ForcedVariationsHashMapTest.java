package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForcedVariationsHashMapTest {


    @Test
    void canBeAHashMap() {
        ForcedVariationsMap forcedVariations = new ForcedVariationsHashMap();
        forcedVariations.put("my-test", 0);
        forcedVariations.put("other-test", 1);

        assertInstanceOf(Map.class, forcedVariations);
    }
}
