package growthbook.sdk.java.repository.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransportResponseTest {

    @Test
    @DisplayName("Finds a header regardless of the case of its name")
    void findsHeaderRegardlessOfCase() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("ETag", "\"etag-1\"");

        // When
        TransportResponse response = TransportResponse.of(200, "OK", "{}", headers);

        // Then
        assertEquals("\"etag-1\"", response.header("etag"));
        assertEquals("\"etag-1\"", response.header("ETAG"));
        assertEquals("\"etag-1\"", response.header("ETag"));
    }

    @Test
    @DisplayName("Returns null for a header that is absent")
    void returnsNullForAbsentHeader() {
        // Given
        TransportResponse response = TransportResponse.of(200, "OK", "{}", Collections.singletonMap("ETag", "\"etag-1\""));

        // When
        String value = response.header("X-Missing");

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Keeps the headers it was built with when the source map changes later")
    void keepsHeadersWhenSourceMapChangesLater() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("ETag", "\"etag-1\"");
        TransportResponse response = TransportResponse.of(200, "OK", "{}", headers);

        // When
        headers.put("ETag", "\"etag-2\"");
        headers.put("X-Added", "later");

        // Then
        assertEquals("\"etag-1\"", response.header("etag"));
        assertNull(response.header("X-Added"));
    }

    @Test
    @DisplayName("Turns a null reason phrase into an empty one")
    void turnsNullMessageIntoEmpty() {
        // Given
        Map<String, String> headers = Collections.emptyMap();

        // When
        TransportResponse response = TransportResponse.of(204, null, null, headers);

        // Then
        assertEquals("", response.message());
    }

    @Test
    @DisplayName("Exposes the status code, reason phrase and body it was built with")
    void exposesCodeMessageAndBody() {
        // Given
        Map<String, String> headers = Collections.emptyMap();

        // When
        TransportResponse response = TransportResponse.of(404, "Not Found", "{\"status\":404}", headers);

        // Then
        assertEquals(404, response.code());
        assertEquals("Not Found", response.message());
        assertEquals("{\"status\":404}", response.body());
    }

    @Test
    @DisplayName("Keeps a missing body as null")
    void keepsMissingBodyAsNull() {
        // Given
        Map<String, String> headers = Collections.emptyMap();

        // When
        TransportResponse response = TransportResponse.of(304, "Not Modified", null, headers);

        // Then
        assertNull(response.body());
    }
}
