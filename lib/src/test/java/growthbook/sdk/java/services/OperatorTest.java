package growthbook.sdk.java.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperatorTest {
    @Test
    void test_Operator_fromString() {
        assertNull(Operator.fromString("$foo"));
        assertEquals(Operator.NIN, Operator.fromString("$nin"));
    }
}
