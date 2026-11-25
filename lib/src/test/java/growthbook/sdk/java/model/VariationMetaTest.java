package growthbook.sdk.java.model;

import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariationMetaTest {

    @Test
    @DisplayName("The builder and the constructor produce the same variation meta")
    void builderAndConstructorProduceSameMeta() {
        // Given
        VariationMeta built = VariationMeta.builder().key("key-1").name("name-1").passThrough(true).build();

        // When
        VariationMeta constructed = new VariationMeta("key-1", "name-1", true);

        // Then
        assertEquals("key-1", built.getKey());
        assertEquals("name-1", built.getName());
        assertTrue(built.getPassThrough());
        assertEquals(built, constructed);
    }

    @Test
    @DisplayName("Reads passThrough from the passthrough JSON field")
    void readsPassThroughFromPassthroughJsonField() {
        // When
        VariationMeta subject = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"key\":\"key-1\",\"name\":\"name-1\",\"passthrough\":true}", VariationMeta.class);

        // Then
        assertEquals(new VariationMeta("key-1", "name-1", true), subject);
    }
}
