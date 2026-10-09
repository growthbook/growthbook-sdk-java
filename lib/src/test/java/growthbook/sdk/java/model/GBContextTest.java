package growthbook.sdk.java.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GBContextTest {

    @Test
    @DisplayName("Verify: positional constructor arities stay available for binary compatibility")
    void publicConstructorSurfaceKeepsEveryPreviouslyAvailableArity() {
        // Positional constructors are part of the published API: dropping one is a binary break
        // (NoSuchMethodError) that still compiles cleanly here. 16 and 23 shipped previously; 26 is the
        // current full signature with the event logger. New parameters must arrive alongside a
        // delegating overload, never by widening an existing signature in place.
        Set<Integer> arities = new HashSet<>();
        for (Constructor<?> constructor : GBContext.class.getConstructors()) {
            arities.add(constructor.getParameterCount());
        }

        assertTrue(arities.containsAll(Arrays.asList(16, 23, 26)),
                "missing positional GBContext constructors, found arities: " + arities);
    }

    @Test
    @DisplayName("Verify: the 23-argument constructor defaults the event-logger settings")
    void legacyConstructorDefaultsEventLoggerSettings() {
        GBContext context = new GBContext(
                "{}",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNull(context.getEventLogger());
        assertNull(context.getEventLoggerExecutor());
        assertFalse(context.getDeferTrackingCalls());
    }
}
