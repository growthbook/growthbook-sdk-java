package growthbook.sdk.java.evaluators.internal;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForcedValuesTest {

    @Test
    @DisplayName("Parses true, on and 1 as a forced true boolean regardless of case")
    void parsesTruthyBooleanValues() throws MalformedURLException {
        // Given
        URL trueUrl = new URL("http://localhost/?gb~flag=TRUE");
        URL onUrl = new URL("http://localhost/?gb~flag=on");
        URL oneUrl = new URL("http://localhost/?gb~flag=1");

        // When
        Object fromTrue = ForcedValues.getForcedValueFromUrl("flag", trueUrl, Boolean.class);
        Object fromOn = ForcedValues.getForcedValueFromUrl("flag", onUrl, Boolean.class);
        Object fromOne = ForcedValues.getForcedValueFromUrl("flag", oneUrl, Boolean.class);

        // Then
        assertEquals(Boolean.TRUE, fromTrue);
        assertEquals(Boolean.TRUE, fromOn);
        assertEquals(Boolean.TRUE, fromOne);
    }

    @Test
    @DisplayName("Parses false, off and 0 as a forced false boolean regardless of case")
    void parsesFalsyBooleanValues() throws MalformedURLException {
        // Given
        URL falseUrl = new URL("http://localhost/?gb~flag=false");
        URL offUrl = new URL("http://localhost/?gb~flag=OFF");
        URL zeroUrl = new URL("http://localhost/?gb~flag=0");

        // When
        Object fromFalse = ForcedValues.getForcedValueFromUrl("flag", falseUrl, Boolean.class);
        Object fromOff = ForcedValues.getForcedValueFromUrl("flag", offUrl, Boolean.class);
        Object fromZero = ForcedValues.getForcedValueFromUrl("flag", zeroUrl, Boolean.class);

        // Then
        assertEquals(Boolean.FALSE, fromFalse);
        assertEquals(Boolean.FALSE, fromOff);
        assertEquals(Boolean.FALSE, fromZero);
    }

    @Test
    @DisplayName("Returns null for a boolean value that is not a recognised boolean")
    void returnsNullForUnrecognisedBoolean() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~flag=yes");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("flag", url, Boolean.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Returns a forced string value decoded from the URL")
    void parsesStringValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~greeting=hello%20world");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("greeting", url, String.class);

        // Then
        assertEquals("hello world", value);
    }

    @Test
    @DisplayName("Parses a forced integer value")
    void parsesIntegerValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~limit=42");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("limit", url, Integer.class);

        // Then
        assertEquals(Integer.valueOf(42), value);
    }

    @Test
    @DisplayName("Returns null for an integer value that is not an integer")
    void returnsNullForNonIntegerValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~limit=4.2");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("limit", url, Integer.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Parses a forced float value")
    void parsesFloatValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~ratio=0.5");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("ratio", url, Float.class);

        // Then
        assertEquals(Float.valueOf(0.5f), value);
    }

    @Test
    @DisplayName("Returns null for a float value that is not a number")
    void returnsNullForNonNumericFloat() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~ratio=half");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("ratio", url, Float.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Parses a forced double value")
    void parsesDoubleValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~price=9.99");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("price", url, Double.class);

        // Then
        assertEquals(Double.valueOf(9.99), value);
    }

    @Test
    @DisplayName("Returns null for a double value that is not a number")
    void returnsNullForNonNumericDouble() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~price=cheap");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("price", url, Double.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Deserializes a forced JSON object value into the requested type")
    void parsesJsonObjectValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~config=%7B%22color%22%3A%22blue%22%2C%22size%22%3A3%7D");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("config", url, JsonObject.class);

        // Then
        JsonObject json = (JsonObject) value;
        assertEquals("blue", json.get("color").getAsString());
        assertEquals(3, json.get("size").getAsInt());
    }

    @Test
    @DisplayName("Returns null for a JSON value that cannot be deserialized into the requested type")
    void returnsNullForUnparseableJson() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~config=%7Bnot-json");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("config", url, JsonObject.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Returns null when the feature key is absent from the query string")
    void returnsNullForAbsentFeatureKey() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~other=true");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("flag", url, Boolean.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Returns null when the URL has no query string")
    void returnsNullWithoutQueryString() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("flag", url, String.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Ignores a query parameter without the gb~ prefix")
    void ignoresUnprefixedParameter() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?flag=true");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("flag", url, Boolean.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Returns the variation index forced through the query string")
    void returnsQueryStringOverrideInRange() {
        // Given
        String url = "http://localhost/?exp-1=1";

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertEquals(Integer.valueOf(1), override);
    }

    @Test
    @DisplayName("Returns variation zero forced through the query string")
    void returnsQueryStringOverrideAtLowerBound() {
        // Given
        String url = "http://localhost/?exp-1=0";

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertEquals(Integer.valueOf(0), override);
    }

    @Test
    @DisplayName("Returns null for a forced variation equal to the number of variations")
    void returnsNullForQueryStringOverrideAtUpperBound() {
        // Given
        String url = "http://localhost/?exp-1=2";

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertNull(override);
    }

    @Test
    @DisplayName("Returns null for a negative forced variation")
    void returnsNullForNegativeQueryStringOverride() {
        // Given
        String url = "http://localhost/?exp-1=-1";

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertNull(override);
    }

    @Test
    @DisplayName("Returns null for a forced variation that is not an integer")
    void returnsNullForNonIntegerQueryStringOverride() {
        // Given
        String url = "http://localhost/?exp-1=one";

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertNull(override);
    }

    @Test
    @DisplayName("Returns null when the experiment key is absent from the query string")
    void returnsNullForAbsentExperimentKey() {
        // Given
        String url = "http://localhost/?other-exp=1";

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertNull(override);
    }

    @Test
    @DisplayName("Returns null for a null, empty or malformed page URL")
    void returnsNullForMissingOrMalformedUrl() {
        // Given
        String nullUrl = null;
        String emptyUrl = "";
        String malformedUrl = "not a url";

        // When
        Integer fromNull = ForcedValues.getQueryStringOverride("exp-1", nullUrl, 2);
        Integer fromEmpty = ForcedValues.getQueryStringOverride("exp-1", emptyUrl, 2);
        Integer fromMalformed = ForcedValues.getQueryStringOverride("exp-1", malformedUrl, 2);

        // Then
        assertNull(fromNull);
        assertNull(fromEmpty);
        assertNull(fromMalformed);
    }
}
