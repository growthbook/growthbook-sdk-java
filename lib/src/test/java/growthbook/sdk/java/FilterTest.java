package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class FilterTest {
    @Test
    void test_canBeBuilt() {
        ArrayList<BucketRange> ranges = new ArrayList<>();
        ranges.add(new BucketRange(0.f, 0.2f));
        ranges.add(new BucketRange(0.2f, 0.5f));

        Filter subject = Filter.builder()
            .attribute("user_id")
            .seed("some-seed")
            .hashVersion(1)
            .ranges(ranges)
            .build();

        assertEquals("user_id", subject.getAttribute());
        assertEquals("some-seed", subject.getSeed());
        assertEquals(1, subject.getHashVersion());
        assertEquals(ranges, subject.getRanges());
    }

    @Test
    void test_canBeBuilt_defaults() {
        Filter subject = Filter.builder().build();

        assertEquals("id", subject.getAttribute());
        assertEquals("", subject.getSeed());
        assertEquals(2, subject.getHashVersion());
        assertEquals(0, subject.getRanges().size());
    }

    @Test
    void test_canBeConstructed() {
        ArrayList<BucketRange> ranges = new ArrayList<>();
        ranges.add(new BucketRange(0.f, 0.2f));

        Filter subject = new Filter("seed", ranges, "user_id", 1);

        assertEquals("user_id", subject.getAttribute());
        assertEquals("seed", subject.getSeed());
        assertEquals(1, subject.getHashVersion());
        assertEquals(1, subject.getRanges().size());
    }

    @Test
    void test_canBeConstructed_defaults() {
        Filter subject = new Filter(null, null, null, null);

        assertEquals("id", subject.getAttribute());
        assertEquals("", subject.getSeed());
        assertEquals(2, subject.getHashVersion());
        assertEquals(0, subject.getRanges().size());
    }

    @Test
    void testCanBeSerializedIntoJson() {
        ArrayList<BucketRange> ranges = new ArrayList<>();
        ranges.add(new BucketRange(0.f, 0.2f));
        ranges.add(new BucketRange(0.2f, 0.5f));

        Filter subject = Filter.builder()
            .attribute("user_id")
            .seed("some-seed")
            .hashVersion(2)
            .ranges(ranges)
            .build();

        assertEquals("{\"seed\":\"some-seed\",\"ranges\":[[0.0,0.2],[0.2,0.5]],\"attribute\":\"user_id\",\"hashVersion\":2}", subject.toJson());
    }
}
