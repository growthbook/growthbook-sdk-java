package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Entry point for matching a targeting condition. A condition comes from the payload and is
 * untrusted: a malformed one, or an unknown result, never matches and never throws.
 */
@Slf4j
public final class Conditions {

    private Conditions() {
    }

    /**
     * @param attributes  the user attributes
     * @param condition   the condition
     * @param savedGroups the saved groups references resolve against, or {@code null} for none
     * @return true only if the condition definitely matches
     */
    public static boolean matches(@Nullable JsonObject attributes, JsonObject condition, @Nullable JsonObject savedGroups) {
        try {
            return Boolean.TRUE.equals(CondParser.parse(condition).eval(attributes, new CondScope(savedGroups)));
        } catch (MalformedConditionException e) {
            log.warn("Malformed condition, treating it as not matching: {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }
}
