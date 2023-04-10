package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VariationMetaTest {
    @Test
    void canBeBuilt() {
        VariationMeta subject = VariationMeta
            .builder()
            .key("my-key")
            .name("my-name")
            .passThrough(true)
            .build();

        assertEquals("my-key", subject.getKey());
        assertEquals("my-name", subject.getName());
        assertEquals(true, subject.getPassThrough());
    }

    @Test
    void canBeConstructed() {
        VariationMeta subject = new VariationMeta("my-key", "my-name", true);

        assertEquals("my-key", subject.getKey());
        assertEquals("my-name", subject.getName());
        assertEquals(true, subject.getPassThrough());
    }
}
