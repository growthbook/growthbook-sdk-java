package growthbook.sdk.java.internal.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MathUtilsTest {

    @Test
    void test_clamp() {
        // 5 is OK
        assertEquals(5, MathUtils.clamp(5, 1, 10));
        // 0 is too low, lower range clamped value of 1 returned
        assertEquals(1, MathUtils.clamp(0, 1, 10));
        // 10 is too high, higher range clamped value of 3 returned
        assertEquals(3, MathUtils.clamp(10, 1, 3));
    }
}
