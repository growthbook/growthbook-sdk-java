package growthbook.sdk.java.services;

import growthbook.sdk.java.models.BucketRange;
import growthbook.sdk.java.models.Namespace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GrowthBookUtils {
    /**
     * Hashes a string to a float between 0 and 1.
     * Uses the simple Fowler–Noll–Vo algorithm, specifically fnv32a.
     * @param stringValue  Input string
     * @return  hashed float value
     */
    public static Float hash(String stringValue) {
        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes());
        BigInteger thousand = new BigInteger("1000");
        BigInteger remainder = bigInt.remainder(thousand);

        String remainderAsString = remainder.toString();
        float remainderAsFloat = Float.parseFloat(remainderAsString);
        return remainderAsFloat / 1000f;
    }

    /**
     * This checks if a userId is within an experiment namespace or not.
     * @param userId  The user identifier
     * @param namespace  Namespace to check the user identifier against
     * @return  whether the user is in the namespace
     */
    public static Boolean inNameSpace(String userId, Namespace namespace) {
        Float n = hash(userId + "__" + namespace.getId());
        return n >= namespace.getRangeStart() && n < namespace.getRangeEnd();
    }

    public static Integer chooseVariation(Float n, ArrayList<BucketRange> bucketRanges) {
        for (int i = 0; i < bucketRanges.size(); i++) {
            BucketRange range = bucketRanges.get(i);
            if (n >= range.getRangeStart() && n < range.getRangeEnd()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns an array of floats with numVariations items that are all equal and sum to 1.
     * For example, getEqualWeights(2) would return [0.5, 0.5]
     * @param numberOfVariations The number of variations you would like
     * @return A list of variations
     */
    public static ArrayList<Float> getEqualWeights(Integer numberOfVariations) {
        // Accommodate -1 number of variations
        int size = Math.max(0, numberOfVariations);
        if (size == 0) {
            return new ArrayList<>();
        }

        Float weight = 1f / numberOfVariations;
        return new ArrayList<Float>(Collections.nCopies(size, weight));
    }
}
