package growthbook.sdk.java;

import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BucketRangeTest {
    @Test
    void canBeBuilt() {
        BucketRange subject = BucketRange
                .builder()
                .rangeStart(0.3)
                .rangeEnd(0.7)
                .build();

        assertEquals(0.3, subject.getRangeStart());
        assertEquals(0.7, subject.getRangeEnd());
    }

    @Test
    void canBeConstructed() {
        BucketRange subject = new BucketRange(0.3, 0.7);

        assertEquals(0.3, subject.getRangeStart());
        assertEquals(0.7, subject.getRangeEnd());
    }

    @Test
    void canBeJsonSerialized() {
        BucketRange subject = new BucketRange(0.3, 0.7);

        assertEquals("[0.3,0.7]", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        assertEquals("[0.3,0.7]", subject.toJson());
        assertEquals("[0.3,0.7]", subject.toString());
    }

    @Test
    void canBeJsonDeSerialized() {
        BucketRange subject = GrowthBookJsonUtils.getInstance().gson.fromJson("[0.3,0.7]", BucketRange.class);

        assertEquals(subject.getRangeStart(), 0.3);
        assertEquals(subject.getRangeEnd(), 0.7);
    }

    @Test
    void equalsComparesExactly() {
        BucketRange a = new BucketRange(0.3, 0.7);
        BucketRange same = new BucketRange(0.3, 0.7);
        BucketRange nearby = new BucketRange(0.3 + 1e-9, 0.7);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, nearby);
    }
}
