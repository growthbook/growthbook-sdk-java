package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GBContextTest {
    @Test
    void canBeInitializedWithDefaultValues() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = new GBContext(isEnabled, url, isQaMode);

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = new GBContext(isEnabled, url, isQaMode);

        // Initial state OK
        assertTrue(subject.getEnabled());
        assertFalse(subject.getIsQaMode());
        assertEquals("http://localhost:3000", subject.getUrl());

        // Use setters
        subject.setEnabled(false);
        subject.setIsQaMode(true);
        subject.setUrl("https://docs.growthbook.io/lib/build-your-own");

        assertFalse(subject.getEnabled());
        assertTrue(subject.getIsQaMode());
        assertEquals("https://docs.growthbook.io/lib/build-your-own", subject.getUrl());
    }
}