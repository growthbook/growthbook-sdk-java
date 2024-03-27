package growthbook.sdk.java;

import java.math.BigInteger;
import java.util.List;

class MathUtils {
    private static final BigInteger INIT32 = new BigInteger("811c9dc5", 16);
    private static final BigInteger PRIME32 = new BigInteger("01000193", 16);
    private static final BigInteger MOD32 = new BigInteger("2").pow(32);

    // FNV-1a 32-bit constants
    private static final int FNV_32_PRIME = 0x01000193;
    private static final int FNV_32_INIT = 0x811c9dc5;

    /**
     * Fowler-Noll-Vo algorithm
     * fnv32a returns an integer, so we convert that to a float using a modulus
     *
     * @param data byte list
     * @return BigInteger
     */
    public static int fnv1a_32(byte[] data) {
        int hash = FNV_32_PRIME;
        for (byte b : data) {
            // XOR the bottom with the current octet.
            hash ^= (b & 0xff);
            // Multiply by the 32 bit FNV magic prime mod 2^32
            hash *= FNV_32_INIT;
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
