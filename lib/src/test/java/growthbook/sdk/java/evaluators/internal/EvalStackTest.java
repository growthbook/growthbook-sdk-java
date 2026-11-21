package growthbook.sdk.java.evaluators.internal;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalStackTest {

    @Test
    @DisplayName("Tracks entered features as the current feature and in the chain")
    void tracksEnteredFeatures() {
        // Given
        EvalStack stack = new EvalStack();

        // When
        stack.enter("feature-a");
        stack.enter("feature-b");

        // Then
        assertEquals("feature-b", stack.currentFeature());
        assertTrue(stack.isInChain("feature-a"));
        assertTrue(stack.isInChain("feature-b"));
        assertFalse(stack.isInChain("feature-c"));
    }

    @Test
    @DisplayName("Returns a chain copy that later entries do not change")
    void returnsIndependentChainCopy() {
        // Given
        EvalStack stack = new EvalStack();
        stack.enter("feature-a");

        // When
        Set<String> saved = stack.chain();
        stack.enter("feature-b");

        // Then
        assertEquals(Collections.singleton("feature-a"), saved);
    }

    @Test
    @DisplayName("Restores a saved chain without keeping a reference to it")
    void restoresSavedChain() {
        // Given
        EvalStack stack = new EvalStack();
        stack.enter("feature-a");
        Set<String> saved = stack.chain();
        stack.enter("feature-b");

        // When
        stack.restoreChain(saved);
        saved.add("feature-c");

        // Then
        assertTrue(stack.isInChain("feature-a"));
        assertFalse(stack.isInChain("feature-b"));
        assertFalse(stack.isInChain("feature-c"));
    }

    @Test
    @DisplayName("Keeps the first memoized result for a feature")
    void keepsFirstMemoizedResult() {
        // Given
        EvalStack stack = new EvalStack();
        FeatureResult<Object> first = result(FeatureResultSource.DEFAULT_VALUE);
        FeatureResult<Object> second = result(FeatureResultSource.FORCE);

        // When
        stack.memoize("feature-a", first);
        stack.memoize("feature-a", second);

        // Then
        assertSame(first, stack.memoized("feature-a"));
    }

    @Test
    @DisplayName("Returns null for a feature that has not been memoized")
    void returnsNullForUnmemoizedFeature() {
        // Given
        EvalStack stack = new EvalStack();

        // When
        FeatureResult<Object> memoized = stack.memoized("feature-a");

        // Then
        assertNull(memoized);
    }

    @Test
    @DisplayName("Forgets the chain, current feature and memoized results on reset")
    void forgetsEverythingOnReset() {
        // Given
        EvalStack stack = new EvalStack();
        stack.enter("feature-a");
        stack.memoize("feature-a", result(FeatureResultSource.DEFAULT_VALUE));

        // When
        stack.reset();

        // Then
        assertNull(stack.currentFeature());
        assertFalse(stack.isInChain("feature-a"));
        assertNull(stack.memoized("feature-a"));
    }

    private static FeatureResult<Object> result(FeatureResultSource source) {
        return FeatureResult.builder().source(source).build();
    }
}
