package growthbook.sdk.java;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Model for caring data: variation and versionIsBlocked
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class StickyBucketVariation {
    private Integer variation;

    /**
     * Check if user blocked by sticky bucket version
     */
    private Boolean versionIsBlocked;
}
