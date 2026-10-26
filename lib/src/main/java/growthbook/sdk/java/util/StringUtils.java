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
        String withoutPrefix = input.replaceAll("(^v|\\+.*$)", "");

        // If it's SemVer without a pre-release, add `~` to the end
        // ["1","0","0"] -> ["1","0","0","~"]
        // "~" is the largest ASCII character, so this will make "1.0.0" greater than "1.0.0-beta" for example
        ArrayList<String> parts = new ArrayList<>(Arrays.asList(withoutPrefix.split("[-.]")));

        if (parts.size() == 3) {
            parts.add("~");
        }

        // Left pad each numeric part with spaces so string comparisons will work ("9">"10", but " 9"<"10")
        // Then, join back together into a single string
        return parts.stream()
            .map(part -> part.matches("^[0-9]+$") ? StringUtils.padLeftZeros(part, 5) : part)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining("-"));
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

        // The fewest significant digits that parse back to the same double; rounding the exact
        // value picks the closest such digits, and an even last digit on a tie. 17 always do.
        BigDecimal exact = new BigDecimal(value);
        BigDecimal shortest = exact;
        for (int precision = 1; precision <= 17; precision++) {
            BigDecimal rounded = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
            if (rounded.doubleValue() == value) {
                shortest = rounded;
                break;
            }
        }
        shortest = shortest.stripTrailingZeros();

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
}
