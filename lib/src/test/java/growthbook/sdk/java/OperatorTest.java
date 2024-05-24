package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OperatorTest {
    @Test
    void test_Operator_fromString() {
        assertNull(Operator.fromString("$foo"));
        assertEquals(Operator.NIN, Operator.fromString("$nin"));
    }
}
