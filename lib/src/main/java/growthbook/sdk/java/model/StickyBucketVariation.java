package growthbook.sdk.java.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Result of a sticky bucket lookup: the resolved variation index and whether the user is
 * blocked by the sticky bucket version.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class StickyBucketVariation {

    /**
     * Sentinel variation index meaning "no sticky assignment found".
     */
    public static final int NOT_FOUND = -1;

    /**
     * The resolved variation index, or {@link #NOT_FOUND} when there is no sticky assignment.
     */
    private Integer variation;

    /**
     * {@code true} when the user is blocked by the sticky bucket version; {@code null} when
     * not applicable.
     */
    private Boolean versionIsBlocked;

    /**
     * @return a result meaning no sticky assignment was found.
     */
    public static StickyBucketVariation notFound() {
        return new StickyBucketVariation(NOT_FOUND, null);
    }

    /**
     * @return a result meaning the user is blocked by the sticky bucket version.
     */
    public static StickyBucketVariation blocked() {
        return new StickyBucketVariation(NOT_FOUND, true);
    }

    /**
     * @param variation the resolved variation index
     * @return a result for a found sticky assignment.
     */
    public static StickyBucketVariation found(int variation) {
        return new StickyBucketVariation(variation, null);
    }
}
