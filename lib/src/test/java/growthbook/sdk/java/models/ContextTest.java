package growthbook.sdk.java.models;

import growthbook.sdk.java.TestHelpers.SampleUserAttributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextTest {

    SampleUserAttributes sampleUserAttributes = new SampleUserAttributes("android", "canada");

    @Test
    void canBeConstructed() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = new Context(isEnabled, url, isQaMode, sampleUserAttributes);

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
                .attributes(sampleUserAttributes)
                .url(url)
                .build();

        assertNotNull(subject);
    }

    @Test
    void hasGetterSetterForInitialState() {
        Boolean isEnabled = true;
        Boolean isQaMode = false;
        String url = "http://localhost:3000";

        Context subject = Context
                .builder()
                .enabled(isEnabled)
                .isQaMode(isQaMode)
                .attributes(sampleUserAttributes)
                .url(url)
                .build();

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

    @Test
    void serializableAttributes() {
        Context subject = Context
                .builder()
                .attributes(sampleUserAttributes)
                .build();

        assertEquals("{\"device\":\"android\",\"country\":\"canada\"}", subject.attributes.toJson());
    }
}