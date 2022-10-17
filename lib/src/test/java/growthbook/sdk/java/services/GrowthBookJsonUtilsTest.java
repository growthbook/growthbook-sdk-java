package growthbook.sdk.java.services;

import growthbook.sdk.java.models.BucketRange;
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

    @Test
    void canSerializeBucketRanges() {
        BucketRange subject = new BucketRange(0.3f, 0.7f);

        assertEquals("[0.3,0.7]", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
    }

    @Test
    void canDeserializeBucketRanges() {
        BucketRange subject = GrowthBookJsonUtils.getInstance().gson.fromJson("[0.3,0.7]", BucketRange.class);

        assertEquals(subject.getRangeStart(), 0.3f);
        assertEquals(subject.getRangeEnd(), 0.7f);
    }
}
