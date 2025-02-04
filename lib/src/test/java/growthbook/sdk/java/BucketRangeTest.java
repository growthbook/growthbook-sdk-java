package growthbook.sdk.java;

import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(subject.getRangeStart(), 0.3f);
        assertEquals(subject.getRangeEnd(), 0.7f);
    }
}
