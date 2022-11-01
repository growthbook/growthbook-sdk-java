package growthbook.sdk.java.internal.services;

import java.math.BigInteger;
import java.util.List;

class MathUtils {
    private static final BigInteger INIT32 = new BigInteger("811c9dc5", 16);
    private static final BigInteger PRIME32 = new BigInteger("01000193", 16);
    private static final BigInteger MOD32 = new BigInteger("2").pow(32);

    /**
     * Fowler-Noll-Vo algorithm
     * fnv32a returns an integer, so we convert that to a float using a modulus
     *
     * @param data byte list
     * @return BigInteger
     */
    public static BigInteger fnv1a_32(byte[] data) {
        BigInteger hash = INIT32;

        for (byte b : data) {
            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
            hash = hash.multiply(PRIME32).mod(MOD32);
        }

        return hash;
    }

    /**
     * Given a value, ensures it's clamped between the range provided
     *
     * @param value      The number you want to make sure is between lowerRange and upperRange
     * @param lowerRange The lowest value
     * @param upperRange The highest value
     * @return the clamped number
     */
    public static float clamp(float value, float lowerRange, float upperRange) {
        if (value > upperRange) return upperRange;
        if (value < lowerRange) return lowerRange;
        return value;
    }

    /**
     * Add up all the numbers
     *
     * @param items Numbers to add
     * @return total
     */
    public static float sum(List<Float> items) {
        float total = 0;

        for (float item : items) {
            total += item;
        }

        return total;
    }
}
