package growthbook.sdk.java.evaluators.internal.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.internal.condition.JsonValues;
import growthbook.sdk.java.model.PayloadFilters;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The {@code include}/{@code exclude} filters on project, tag and custom-field metadata.
 */
final class PayloadFilter {

    private static final String PROJECTS = "projects";
    private static final String TAGS = "tags";

    private final PayloadFilters include;
    private final PayloadFilters exclude;

    PayloadFilter(@Nullable PayloadFilters include, @Nullable PayloadFilters exclude) {
        this.include = include != null ? include : new PayloadFilters();
        this.exclude = exclude != null ? exclude : new PayloadFilters();
    }

    /**
     * Kept when it matches every {@code include} filter and no {@code exclude} filter. Only
     * listed projects are excluded, so one in all projects stays.
     */
    boolean matches(JsonObject metadata) {
        return inProjects(metadata) && matchesIncluded(metadata) && !matchesExcluded(metadata);
    }

    /**
     * A feature or experiment has to list one of the projects, or be in all of them. One
     * without projects is in none, like one without tags.
     */
    boolean inProjects(JsonObject metadata) {
        return include.getProjects() == null
                || JsonValues.isTruthy(metadata.get("allProjects"))
                || has(metadata.get(PROJECTS), include.getProjects());
    }

    /**
     * A rule without projects applies wherever its feature is delivered.
     */
    boolean ruleInProjects(@Nullable JsonElement metadata) {
        if (include.getProjects() == null || metadata == null || !metadata.isJsonObject()) {
            return true;
        }
        JsonElement projects = metadata.getAsJsonObject().get(PROJECTS);
        return !JsonValues.isTruthy(projects) || has(projects, include.getProjects());
    }

    private boolean matchesIncluded(JsonObject metadata) {
        if (include.getTags() != null && !has(metadata.get(TAGS), include.getTags())) {
            return false;
        }
        if (include.getCustomFields() == null) {
            return true;
        }
        for (Map.Entry<String, List<String>> field : include.getCustomFields().entrySet()) {
            if (!has(customField(metadata, field.getKey()), field.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesExcluded(JsonObject metadata) {
        if (has(metadata.get(PROJECTS), exclude.getProjects()) || has(metadata.get(TAGS), exclude.getTags())) {
            return true;
        }
        if (exclude.getCustomFields() == null) {
            return false;
        }
        for (Map.Entry<String, List<String>> field : exclude.getCustomFields().entrySet()) {
            if (has(customField(metadata, field.getKey()), field.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static JsonElement customField(JsonObject metadata, String key) {
        JsonElement fields = metadata.get("customFields");
        return fields != null && fields.isJsonObject() ? fields.getAsJsonObject().get(key) : null;
    }

    /**
     * Only a list counts, as with {@code $in}, and any one of its values is enough.
     */
    private static boolean has(@Nullable JsonElement actual, @Nullable List<String> want) {
        if (want == null) {
            return false;
        }
        JsonArray values = new JsonArray();
        for (String value : want) {
            values.add(value);
        }
        return JsonValues.isIn(actual, values, false);
    }
}
