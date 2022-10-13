package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GBBucketRangeTest {
    @Test
    void behavesLikeABucketRange() {
        BucketRange subject = new GBBucketRange(0.3f, 0.7f);

        assertEquals(0.3f, subject.getStart());
        assertEquals(0.7f, subject.getEnd());
    }
}