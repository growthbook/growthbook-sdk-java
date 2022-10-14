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
}
