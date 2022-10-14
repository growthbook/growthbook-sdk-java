package growthbook.sdk.java.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextTest {
    @Test
    void canBeConstructed() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = new Context(isEnabled, url, isQaMode);

        assertNotNull(subject);
    }

    @Test
    void canBeBuilt() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = Context
                .builder()
                .enabled(isEnabled)
                .isQaMode(isQaMode)
                .url(url)
                .build();

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = new Context(isEnabled, url, isQaMode);

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