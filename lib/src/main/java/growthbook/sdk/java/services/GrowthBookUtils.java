package growthbook.sdk.java.services;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class GrowthBookUtils {
    public static Float hash(String stringValue) {
        BigInteger bigInt = MathUtils.fnv1_32(stringValue.getBytes());
        BigInteger thousand = new BigInteger("1000");
        BigInteger modded = bigInt.mod(thousand);

        return modded.divide(thousand).floatValue();
    }
}
