package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashVersionTest {
    @Test
    void test_toString() {
        assertEquals("1", HashVersion.V1.toString());
        assertEquals("2", HashVersion.V2.toString());
    }

    @Test
    void test_intValue() {
        assertEquals(1, HashVersion.V1.intValue());
        assertEquals(2, HashVersion.V2.intValue());
    }

    @Test
    void test_fromInt() {
        assertEquals(HashVersion.V1, HashVersion.fromInt(1));
        assertEquals(HashVersion.V2, HashVersion.fromInt(2));
        assertNull(HashVersion.fromInt(3));
    }
}
