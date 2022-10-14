package growthbook.sdk.java.services;

import java.math.BigInteger;

public class GrowthBookUtils {
    public static Float hash(String stringValue) {
        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes());
//        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes(StandardCharsets.UTF_8));
        float floatValue = bigInt.floatValue();

        BigInteger thousand = new BigInteger("1000");
        float thousandFloat = thousand.floatValue();

        return (floatValue % thousandFloat) / thousandFloat;
    }
}
