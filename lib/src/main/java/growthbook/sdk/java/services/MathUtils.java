package growthbook.sdk.java.services;

import java.math.BigInteger;

public class MathUtils {
    private static final BigInteger INIT32  = new BigInteger("811c9dc5", 16);
    private static final BigInteger PRIME32 = new BigInteger("01000193", 16);
    private static final BigInteger MOD32   = new BigInteger("2").pow(32);

    /**
     * Fowler-Noll-Vo algorithm
     * fnv32a returns an integer, so we convert that to a float using a modulus
     * Source: <a href="https://github.com/jakedouglas/fnv-java/blob/master/src/main/java/com/bitlove/FNV.java">jakedouglas/fnv-java</a>
     * @param data
     * @return BigInteger
     */
    public static BigInteger fnv1_32(byte[] data) {
        BigInteger hash = INIT32;

        for (byte b : data) {
            hash = hash.multiply(PRIME32).mod(MOD32);
            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
        }

        return hash;
    }

    /**
     * Fowler-Noll-Vo algorithm
     * fnv32a returns an integer, so we convert that to a float using a modulus
     * Source: <a href="https://github.com/jakedouglas/fnv-java/blob/master/src/main/java/com/bitlove/FNV.java">jakedouglas/fnv-java</a>
     * @param data
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

    // https://github.com/prasanthj/hasher/blob/master/src/main/java/hasher/FNV1a.java
    public static int hash32(byte[] data) {
        return hash32(data, data.length);
    }

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 16777619;

    /**
     * FNV1a 32 bit variant.
     *
     * @param data   - input byte array
     * @param length - length of array
     * @return - hashcode
     */
    public static int hash32(byte[] data, int length) {
        int hash = FNV1_32_INIT;
        for (int i = 0; i < length; i++) {
            hash ^= (data[i] & 0xff);
            hash *= FNV1_PRIME_32;
        }

        return hash;
    }
}
