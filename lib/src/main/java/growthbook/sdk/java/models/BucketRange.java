package growthbook.sdk.java.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * A tuple that describes a range of the number line between 0 and 1.
 * The tuple has 2 parts, both floats - the start of the range and the end.
 */
@Data @Builder @AllArgsConstructor
public class BucketRange {
    Float rangeStart;
    Float rangeEnd;
}
