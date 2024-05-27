package growthbook.sdk.java;

import com.google.common.base.Splitter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class UrlUtils {
    /**
     * Parse a query string into a map of key/value pairs.
     *
     * @param queryString the string to parse (without the '?')
     * @return key/value pairs mapping to the items in the query string
     */
    public static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> map = new HashMap<>();
        if ((queryString == null) || queryString.isEmpty()) {
            return map;
        }
        Iterable<String> params = Splitter.on('&').split(queryString);
        for (String param : params) {
            String[] keyValuePair = param.split("=", 2);
            String name = URLDecoder.decode(keyValuePair[0], StandardCharsets.UTF_8);
            if (Objects.equals(name, "")) {
                continue;
            }
            String value = keyValuePair.length > 1 ? URLDecoder.decode(keyValuePair[1], StandardCharsets.UTF_8) : "";

            map.put(name, value);
        }
        return map;
    }
}
