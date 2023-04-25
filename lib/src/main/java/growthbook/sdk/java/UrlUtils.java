package growthbook.sdk.java;

import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
class UrlUtils {
    /**
     * Parse a query string into a map of key/value pairs.
     *
     * @param queryString the string to parse (without the '?')
     * @return key/value pairs mapping to the items in the query string
     */
    public static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> map = new HashMap<String, String>();
        if ((queryString == null) || (queryString.equals(""))) {
            return map;
        }
        String[] params = queryString.split("&");
        for (String param : params) {
            try {
                String[] keyValuePair = param.split("=", 2);
                String name = URLDecoder.decode(keyValuePair[0], "UTF-8");
                if (Objects.equals(name, "")) {
                    continue;
                }
                String value = keyValuePair.length > 1 ? URLDecoder.decode(keyValuePair[1], "UTF-8") : "";

                map.put(name, value);
            } catch (UnsupportedEncodingException e) {
                log.error("Unable to parse query string [{}]", queryString, e);
            }
        }
        return map;
    }
}
