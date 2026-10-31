package growthbook.sdk.java.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterTest {

    @Test
    @DisplayName("The builder and the constructor keep the given values")
    void builderAndConstructorKeepGivenValues() {
        // Given
        List<BucketRange> ranges = Arrays.asList(new BucketRange(0.0, 0.2), new BucketRange(0.2, 0.5));

        // When
        Filter built = Filter.builder().attribute("user_id").seed("seed-1").hashVersion(1).ranges(ranges).build();
        Filter constructed = new Filter("seed-1", ranges, "user_id", 1);

        // Then
        for (Filter subject : Arrays.asList(built, constructed)) {
            assertEquals("user_id", subject.getAttribute());
            assertEquals("seed-1", subject.getSeed());
            assertEquals(1, subject.getHashVersion());
            assertEquals(ranges, subject.getRanges());
        }
    }

    @Test
    @DisplayName("Defaults to the id attribute, an empty seed, hash version 2 and no ranges")
    void appliesDefaultsForMissingValues() {
        // When
        Filter built = Filter.builder().build();
        Filter constructed = new Filter(null, null, null, null);

        // Then
        for (Filter subject : Arrays.asList(built, constructed)) {
            assertEquals("id", subject.getAttribute());
            assertEquals("", subject.getSeed());
            assertEquals(2, subject.getHashVersion());
            assertEquals(Collections.emptyList(), subject.getRanges());
        }
    }

    @Test
    @DisplayName("Serializes ranges as JSON tuples")
    void serializesRangesAsJsonTuples() {
        // Given
        Filter subject = Filter.builder()
                .attribute("user_id")
                .seed("seed-1")
                .hashVersion(2)
                .ranges(Arrays.asList(new BucketRange(0.0, 0.2), new BucketRange(0.2, 0.5)))
                .build();

        // When
        String json = subject.toJson();

        // Then
        assertEquals("{\"seed\":\"seed-1\",\"ranges\":[[0.0,0.2],[0.2,0.5]],\"attribute\":\"user_id\",\"hashVersion\":2}", json);
        assertEquals(json, subject.toString());
    }
}
