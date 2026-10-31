package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import growthbook.sdk.java.model.VariationMeta;
import org.junit.jupiter.api.Test;

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
