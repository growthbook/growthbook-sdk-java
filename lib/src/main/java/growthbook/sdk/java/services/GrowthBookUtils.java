package growthbook.sdk.java.services;

import java.math.BigInteger;

public class GrowthBookUtils {
    // Like this: https://github.com/growthbook/growthbook-kotlin/blob/main/GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/Utils/GBUtils.kt#L50-L54
    public static Float hash(String stringValue) {
        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes());
        BigInteger thousand = new BigInteger("1000");
        BigInteger remainder = bigInt.remainder(thousand);

        String remainderAsString = remainder.toString();
        float remainderAsFloat = Float.parseFloat(remainderAsString);
        return remainderAsFloat / 1000f;
    }

    // Like this: https://github.com/jakedouglas/fnv-java/blob/master/src/main/java/com/bitlove/FNV.java#L35-L44
//    public static Float hash(String stringValue) {
//        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes());
////        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes(StandardCharsets.UTF_8));
//        float floatValue = bigInt.floatValue();
//
//        BigInteger thousand = new BigInteger("1000");
//        float thousandFloat = thousand.floatValue();
//
//        return (floatValue % thousandFloat) / thousandFloat;
//    }

    // Like this: https://github.com/growthbook/growthbook-kotlin/blob/main/GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/Utils/GBUtils.kt#L50-L54
//    public static Float hash(String stringValue) {
//        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes());
//        BigInteger thousand = new BigInteger("1000");
//        BigInteger remainder = bigInt.remainder(thousand);
//
//        String remainderAsString = remainder.toString();
//        float remainderAsFloat = Float.parseFloat(remainderAsString);
//        return remainderAsFloat / 1000f;
//    }

    // From here: https://github.com/prasanthj/hasher/blob/master/src/main/java/hasher/FNV1a.java
//    public static Float hash(String stringValue) {
//        int i = MathUtils.hash32(stringValue.getBytes());
////        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes(StandardCharsets.UTF_8));
//        float floatValue = (float) i;
//
//        float thousand = 1000f;
////        BigInteger thousand = new BigInteger("1000");
////        float thousandFloat = thousand.floatValue();
//
//        return (floatValue % thousand) / thousand;
//    }

    // http://www.java2s.com/Code/Java/Development-Class/FNVHash.htm
//    public static Float hash(String stringValue) {
//        int i = FNVHash.hash32(stringValue);
//        return (float) i;
//    }
}
