package growthbook.sdk.java.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class StringUtils {
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
}
