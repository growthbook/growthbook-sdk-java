package growthbook.sdk.java.model;

import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contextual bandit fields added a parameter to the Lombok all-args constructors of
 * {@link Experiment} and {@link FeatureRule} and to {@link GlobalContext}. Those constructors are
 * public API: dropping an arity is a binary break (NoSuchMethodError) that still compiles. These
 * checks pin the pre-contextual-bandit arities alongside the new ones so a delegating overload is
 * never removed.
 */
class BanditConstructorCompatibilityTest {

    @Test
    @DisplayName("Verify: Experiment keeps its pre-bandit all-args constructor arity")
    void experimentPreservesConstructorArities() {
        assertTrue(arities(Experiment.class).containsAll(setOf(21, 22, 23)),
                "missing Experiment constructor arities, found: " + arities(Experiment.class));
    }

    @Test
    @DisplayName("Verify: FeatureRule keeps its pre-bandit all-args constructor arity")
    void featureRulePreservesConstructorArities() {
        assertTrue(arities(FeatureRule.class).containsAll(setOf(23, 24, 26)),
                "missing FeatureRule constructor arities, found: " + arities(FeatureRule.class));
    }

    @Test
    @DisplayName("Verify: GlobalContext keeps its pre-bandit constructor arity")
    void globalContextPreservesConstructorArities() {
        assertTrue(arities(GlobalContext.class).containsAll(setOf(7, 8)),
                "missing GlobalContext constructor arities, found: " + arities(GlobalContext.class));
    }

    private static Set<Integer> arities(Class<?> type) {
        Set<Integer> arities = new HashSet<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            arities.add(constructor.getParameterCount());
        }
        return arities;
    }

    private static Set<Integer> setOf(int... values) {
        Set<Integer> set = new HashSet<>();
        for (int value : values) {
            set.add(value);
        }
        return set;
    }
}
