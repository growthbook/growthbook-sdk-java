package growthbook.sdk.java.models;

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
}