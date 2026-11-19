package growthbook.sdk.java.util;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

    public static String normalize(@Nullable String value) {
        return value == null ? "" : value;
    }

    public static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String padLeftZeros(String inputString, Integer length) {
        if (inputString.length() >= length) {
            return inputString;
        }
        StringBuilder stringBuilder = new StringBuilder();
        while (stringBuilder.length() < length - inputString.length()) {
            stringBuilder.append('0');
        }
        stringBuilder.append(inputString);

        return stringBuilder.toString();
    }

    public static String paddedVersionString(String input) {
        // Like the JS SDK, an empty or null version is version 0
        if (input == null || input.isEmpty()) {
            input = "0";
        }
        // Remove build info and leading `v` if any
        // Split version into parts (both core version numbers and pre-release tags)
        // "v1.2.3-rc.1+build123" -> ["1","2","3","rc","1"]
        String version = input.startsWith("v") ? input.substring(1) : input;
        int buildIndex = version.indexOf('+');
        if (buildIndex != -1) {
            version = version.substring(0, buildIndex);
        }

        // If it's SemVer without a pre-release, add `~` to the end
        // ["1","0","0"] -> ["1","0","0","~"]
        // "~" is the largest ASCII character, so this will make "1.0.0" greater than "1.0.0-beta" for example
        // Keep empty parts, like JS split: "-1" is ["", "1"]
        ArrayList<String> parts = new ArrayList<>(Arrays.asList(version.split("[-.]", -1)));

        if (parts.size() == 3) {
            parts.add("~");
        }

        // Left pad each numeric part with spaces so string comparisons will work ("9">"10", but " 9"<"10").
        // Spaces, not zeros, like the JS SDK: a part that isn't all digits, such as " 5", must sort the same way
        return parts.stream()
            .map(part -> part.matches("^[0-9]+$") ? padLeftSpaces(part, 5) : part)
            .collect(Collectors.joining("-"));
    }

    private static String padLeftSpaces(String inputString, int length) {
        StringBuilder stringBuilder = new StringBuilder();
        while (stringBuilder.length() < length - inputString.length()) {
            stringBuilder.append(' ');
        }
        return stringBuilder.append(inputString).toString();
    }

    /**
     * Formats a number like JavaScript's {@code String(number)} (ECMAScript Number::toString), so
     * numeric operands compare like they do in the JS SDK. {@link Double#toString} can't be used:
     * before JDK 19 it doesn't always give the shortest digits, and it uses a different notation.
     *
     * @param value the number to format
     * @return the shortest decimal digits that round-trip to {@code value}, in plain notation
     *         unless the decimal exponent is 21 or more, or -7 or less (for example
     *         {@code "1000"}, {@code "1.5"}, {@code "0.000002"}, {@code "1e+21"}, {@code "1e-7"})
     */
    public static String jsNumberToString(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == 0) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value < 0) {
            return "-" + jsNumberToString(-value);
        }

        BigDecimal shortest = shortestRoundTrip(value);

        // value = 0.digits * 10^n
        String digits = shortest.unscaledValue().toString();
        int k = digits.length();
        int n = k - shortest.scale();
        StringBuilder out = new StringBuilder();
        if (k <= n && n <= 21) {
            out.append(digits);
            for (int i = k; i < n; i++) {
                out.append('0');
            }
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.");
            for (int i = n; i < 0; i++) {
                out.append('0');
            }
            out.append(digits);
        } else {
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            out.append('e').append(n - 1 >= 0 ? '+' : '-').append(Math.abs(n - 1));
        }
        return out.toString();
    }

    /**
     * Finds the fewest significant digits that parse back to {@code value}, as ECMAScript
     * Number::toString requires: of the candidates with that many digits, the one closest to the
     * exact value, and the one with an even last digit on a tie.
     *
     * @param value a finite, positive double
     * @return those digits, without trailing zeros
     */
    private static BigDecimal shortestRoundTrip(double value) {
        BigDecimal exact = new BigDecimal(value);
        // 17 significant digits always round-trip
        for (int precision = 1; precision <= 17; precision++) {
            // Only the candidates just below and just above the exact value can round-trip at this
            // precision. Rounding to the nearest alone isn't enough: next to a power of two the
            // double's rounding interval is lopsided, so only the farther candidate may parse back.
            BigDecimal below = exact.round(new MathContext(precision, RoundingMode.FLOOR));
            BigDecimal above = exact.round(new MathContext(precision, RoundingMode.CEILING));
            boolean belowMatches = Double.parseDouble(below.toString()) == value;
            boolean aboveMatches = Double.parseDouble(above.toString()) == value;
            if (belowMatches && aboveMatches) {
                return exact.round(new MathContext(precision, RoundingMode.HALF_EVEN)).stripTrailingZeros();
            }
            if (belowMatches) {
                return below.stripTrailingZeros();
            }
            if (aboveMatches) {
                return above.stripTrailingZeros();
            }
        }
        return exact.round(new MathContext(17, RoundingMode.HALF_EVEN)).stripTrailingZeros();
    }
}
