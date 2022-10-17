package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Namespace;

import java.math.BigInteger;

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
}
