package growthbook.sdk.java;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class StickyBucketVariation {
    private Integer variation;
    private Boolean versionIsBlocked;
}
