package growthbook.sdk.java.models;

import java.util.HashMap;

/**
 * A convenient HashMap class for working with forced variations.
 * See {@link Context}
 * See {@link ForcedVariationsMap}
 */
public class ForcedVariationsHashMap extends HashMap<String, Integer> implements ForcedVariationsMap {
    public ForcedVariationsHashMap() {
        super();
    }
}
