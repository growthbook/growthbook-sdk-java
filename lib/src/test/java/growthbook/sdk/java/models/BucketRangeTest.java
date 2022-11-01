package growthbook.sdk.java.models;

import growthbook.sdk.java.internal.services.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BucketRangeTest {
    @Test
    void canBeBuilt() {
        BucketRange subject = BucketRange
                .builder()
                .rangeStart(0.3f)
                .rangeEnd(0.7f)
                .build();

        assertEquals(0.3f, subject.getRangeStart());
        assertEquals(0.7f, subject.getRangeEnd());
    }

    @Test
    void canBeConstructed() {
        BucketRange subject = new BucketRange(0.3f, 0.7f);

        assertEquals(0.3f, subject.getRangeStart());
        assertEquals(0.7f, subject.getRangeEnd());
    }

    @Test
    void canBeJsonSerialized() {
        BucketRange subject = new BucketRange(0.3f, 0.7f);

        assertEquals("[0.3,0.7]", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        assertEquals("[0.3,0.7]", subject.toJson());
        assertEquals("[0.3,0.7]", subject.toString());
    }

    @Test
    void canBeJsonDeSerialized() {
        BucketRange subject = GrowthBookJsonUtils.getInstance().gson.fromJson("[0.3,0.7]", BucketRange.class);

        assertEquals(subject.rangeStart, 0.3f);
        assertEquals(subject.rangeEnd, 0.7f);
    }
}
