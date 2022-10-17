package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Namespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrowthBookJsonUtilsTest {
    GrowthBookJsonUtils subject = GrowthBookJsonUtils.getInstance();

    @Test
    void canSerializeNamespaces() {
        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        assertEquals("[\"pricing\",0.0,0.6]", subject.gson.toJson(namespace));
    }

    @Test
    void canDeSerializeNamespaces() {
        Namespace namespace = subject.gson.fromJson("[\"pricing\",0.0,0.6]", Namespace.class);

        assertEquals(namespace.getId(), "pricing");
        assertEquals(namespace.getRangeStart(), 0.0f);
        assertEquals(namespace.getRangeEnd(), 0.6f);
    }
}
