package growthbook.sdk.java.util;

import java.util.List;

public class MathUtils {
    private static final long INIT32 = 0x811c9dc5L;
    private static final long PRIME32 = 0x01000193L;
    private static final long MOD32 = 1L << 32;

    /**
     * Fowler-Noll-Vo algorithm
     * fnv32a returns a long, so we convert that to a float using a modulus
     *
     * @param data byte list
     * @return long
     */
    public static long fnv1a_32(byte[] data) {
        long hash = INIT32;

        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= PRIME32;
            hash %= MOD32;
        }

        return hash;
    }

    /**
     * FNV-1a over the string's UTF-16 code units, matching the reference SDK's {@code charCodeAt}
     * hashing. Each code unit is XOR-ed whole (0..0xFFFF), so non-ASCII characters hash identically
     * to the JavaScript SDK; for ASCII this is identical to {@link #fnv1a_32(byte[])}.
     *
     * @param data string to hash
     * @return the 32-bit FNV-1a hash
     */
    public static long fnv1a_32(String data) {
        long hash = INIT32;

        for (int i = 0; i < data.length(); i++) {
            hash ^= (data.charAt(i) & 0xffff);
            hash *= PRIME32;
            hash %= MOD32;
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
