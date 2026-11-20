package growthbook.sdk.java.model;

import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BucketRangeTest {

    @Test
    @DisplayName("The builder and the constructor produce the same range")
    void builderAndConstructorProduceSameRange() {
        // Given
        BucketRange built = BucketRange.builder().rangeStart(0.3).rangeEnd(0.7).build();

        // When
        BucketRange constructed = new BucketRange(0.3, 0.7);

        // Then
        assertEquals(0.3, built.getRangeStart());
        assertEquals(0.7, built.getRangeEnd());
        assertEquals(built, constructed);
    }

    @Test
    @DisplayName("Serializes to a two-element JSON tuple")
    void serializesToTwoElementTuple() {
        // Given
        BucketRange subject = new BucketRange(0.3, 0.7);

        // When
        String gsonJson = GrowthBookJsonUtils.getInstance().gson.toJson(subject);

        // Then
        assertEquals("[0.3,0.7]", gsonJson);
        assertEquals("[0.3,0.7]", subject.toJson());
        assertEquals("[0.3,0.7]", subject.toString());
    }

    @Test
    @DisplayName("Deserializes from a two-element JSON tuple")
    void deserializesFromTwoElementTuple() {
        // When
        BucketRange subject = GrowthBookJsonUtils.getInstance().gson.fromJson("[0.3,0.7]", BucketRange.class);

        // Then
        assertEquals(new BucketRange(0.3, 0.7), subject);
    }

    @Test
    @DisplayName("A JSON round trip keeps full double precision")
    void jsonRoundTripKeepsFullDoublePrecision() {
        // Given
        String json = "[0.020000000000000004,0.6000000000000001]";

        // When
        BucketRange subject = GrowthBookJsonUtils.getInstance().gson.fromJson(json, BucketRange.class);

        // Then
        assertEquals(0.020000000000000004, subject.getRangeStart());
        assertEquals(0.6000000000000001, subject.getRangeEnd());
        assertEquals(json, GrowthBookJsonUtils.getInstance().gson.toJson(subject));
        assertEquals(json, new BucketRange(0.020000000000000004, 0.6000000000000001).toJson());
        assertEquals(subject, GrowthBookJsonUtils.getInstance().gson.fromJson(subject.toJson(), BucketRange.class));
    }

    @Test
    @DisplayName("Equality compares bounds exactly")
    void equalityComparesBoundsExactly() {
        // Given
        BucketRange range = new BucketRange(0.3, 0.7);
        BucketRange same = new BucketRange(0.3, 0.7);
        BucketRange nearby = new BucketRange(0.3 + 1e-9, 0.7);

        // When / Then
        assertEquals(range, same);
        assertEquals(range.hashCode(), same.hashCode());
        assertNotEquals(range, nearby);
    }
}
