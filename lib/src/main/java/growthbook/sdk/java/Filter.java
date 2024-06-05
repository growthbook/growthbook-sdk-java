package growthbook.sdk.java;

import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Object used for mutual exclusion and filtering users out of experiments based on random hashes.
 */
@Getter
public class Filter {
    /**
     * The seed used in the hash
     */
    String seed;

    /**
     * Array of ranges that are included
     */
    List<BucketRange> ranges;

    /**
     * The attribute to use (default to "id")
     */
    String attribute;

    /**
     * The hash version to use (default to 2)
     */
    Integer hashVersion;

    /**
     * Object used for mutual exclusion and filtering users out of experiments based on random hashes.
     *
     * @param seed        The seed used in the hash
     * @param ranges      Array of ranges that are included
     * @param attribute   The attribute to use (default: "id")
     * @param hashVersion The hash version to use (default: 2)
     */
    @Builder
    public Filter(
            @Nullable String seed,
            List<BucketRange> ranges,
            String attribute,
            Integer hashVersion
    ) {
        this.seed = seed == null ? "" : seed;
        this.ranges = ranges == null ? new ArrayList<>() : ranges;
        this.attribute = attribute == null ? "id" : attribute;
        this.hashVersion = hashVersion == null ? 2 : hashVersion;
    }

    /**
     * @return JSON string of the {@link Filter}
     */
    public String toJson() {
        return GrowthBookJsonUtils.getInstance().gson.toJson(this);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
