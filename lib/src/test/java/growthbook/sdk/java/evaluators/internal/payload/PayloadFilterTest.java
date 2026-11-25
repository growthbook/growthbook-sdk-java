package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.PayloadFilters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PayloadFilterTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{'projects': ['p1'], 'tags': ['t1'], 'customFields': {'team': 'a'}}", "{'projects': []}"})
    @DisplayName("Keeps every item when there are no filters")
    void keepsEverythingWithoutFilters(String metadata) {
        // Given
        PayloadFilter filter = new PayloadFilter(null, null);

        // When
        boolean matches = filter.matches(json(metadata));
        boolean inProjects = filter.inProjects(json(metadata));

        // Then
        assertTrue(matches);
        assertTrue(inProjects);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'projects': ['p1']}|true",
            "{'projects': ['p3', 'p2']}|true",
            "{'projects': ['p3']}|false",
            "{'projects': []}|false",
            "{}|false",
            "{'allProjects': true}|true",
            "{'allProjects': true, 'projects': ['p3']}|true",
            "{'allProjects': false, 'projects': ['p3']}|false"
    })
    @DisplayName("Keeps an item listing an included project or in all projects")
    void filtersByIncludedProjects(String metadata, boolean expected) {
        // Given
        PayloadFilter filter = new PayloadFilter(PayloadFilters.builder().projects(Arrays.asList("p1", "p2")).build(), null);

        // When
        boolean matches = filter.matches(json(metadata));
        boolean inProjects = filter.inProjects(json(metadata));

        // Then
        assertEquals(expected, matches);
        assertEquals(expected, inProjects);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'tags': ['t1']}|true",
            "{'tags': ['t9', 't2']}|true",
            "{'tags': ['t9']}|false",
            "{'tags': []}|false",
            "{}|false"
    })
    @DisplayName("Keeps an item with one of the included tags")
    void filtersByIncludedTags(String metadata, boolean expected) {
        // Given
        PayloadFilter filter = new PayloadFilter(PayloadFilters.builder().tags(Arrays.asList("t1", "t2")).build(), null);

        // When
        boolean matches = filter.matches(json(metadata));

        // Then
        assertEquals(expected, matches);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'customFields': {'team': 'a', 'tier': 'gold'}}|true",
            "{'customFields': {'team': ['b', 'a'], 'tier': ['gold']}}|true",
            "{'customFields': {'team': 'a', 'tier': 'silver'}}|false",
            "{'customFields': {'team': 'a'}}|false",
            "{'customFields': {}}|false",
            "{}|false"
    })
    @DisplayName("Keeps an item matching every included custom field")
    void filtersByIncludedCustomFields(String metadata, boolean expected) {
        // Given
        PayloadFilters include = PayloadFilters.builder()
                .customFields(customFields("team", "a", "tier", "gold"))
                .build();
        PayloadFilter filter = new PayloadFilter(include, null);

        // When
        boolean matches = filter.matches(json(metadata));

        // Then
        assertEquals(expected, matches);
    }

    @Test
    @DisplayName("Requires an item to match every include filter")
    void requiresEveryIncludeFilter() {
        // Given
        PayloadFilters include = PayloadFilters.builder()
                .projects(Collections.singletonList("p1"))
                .tags(Collections.singletonList("t1"))
                .build();
        PayloadFilter filter = new PayloadFilter(include, null);

        // When
        boolean both = filter.matches(json("{'projects': ['p1'], 'tags': ['t1']}"));
        boolean projectOnly = filter.matches(json("{'projects': ['p1'], 'tags': ['t2']}"));
        boolean tagOnly = filter.matches(json("{'projects': ['p2'], 'tags': ['t1']}"));

        // Then
        assertTrue(both);
        assertFalse(projectOnly);
        assertFalse(tagOnly);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'projects': ['p1']}|false",
            "{'projects': ['p2', 'p1']}|false",
            "{'projects': ['p2']}|true",
            "{'allProjects': true}|true",
            "{}|true"
    })
    @DisplayName("Removes an item listing an excluded project but keeps one in all projects")
    void filtersByExcludedProjects(String metadata, boolean expected) {
        // Given
        PayloadFilter filter = new PayloadFilter(null, PayloadFilters.builder().projects(Collections.singletonList("p1")).build());

        // When
        boolean matches = filter.matches(json(metadata));

        // Then
        assertEquals(expected, matches);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'tags': ['t1']}|false",
            "{'tags': ['t2']}|true",
            "{}|true"
    })
    @DisplayName("Removes an item with an excluded tag")
    void filtersByExcludedTags(String metadata, boolean expected) {
        // Given
        PayloadFilter filter = new PayloadFilter(null, PayloadFilters.builder().tags(Collections.singletonList("t1")).build());

        // When
        boolean matches = filter.matches(json(metadata));

        // Then
        assertEquals(expected, matches);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'customFields': {'team': 'a'}}|false",
            "{'customFields': {'tier': 'gold'}}|false",
            "{'customFields': {'team': 'b', 'tier': 'silver'}}|true",
            "{'customFields': 'team'}|true",
            "{}|true"
    })
    @DisplayName("Removes an item matching any excluded custom field")
    void filtersByExcludedCustomFields(String metadata, boolean expected) {
        // Given
        PayloadFilters exclude = PayloadFilters.builder()
                .customFields(customFields("team", "a", "tier", "gold"))
                .build();
        PayloadFilter filter = new PayloadFilter(null, exclude);

        // When
        boolean matches = filter.matches(json(metadata));

        // Then
        assertEquals(expected, matches);
    }

    @Test
    @DisplayName("Lets an exclude filter remove an item an include filter keeps")
    void excludeWinsOverInclude() {
        // Given
        PayloadFilter filter = new PayloadFilter(
                PayloadFilters.builder().projects(Collections.singletonList("p1")).build(),
                PayloadFilters.builder().tags(Collections.singletonList("t1")).build());

        // When
        boolean matches = filter.matches(json("{'projects': ['p1'], 'tags': ['t1']}"));

        // Then
        assertFalse(matches);
    }

    @Test
    @DisplayName("Treats empty filters like absent ones")
    void emptyFiltersFilterNothing() {
        // Given
        PayloadFilter filter = new PayloadFilter(new PayloadFilters(), new PayloadFilters());

        // When
        boolean matches = filter.matches(new JsonObject());

        // Then
        assertTrue(matches);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "{'projects': ['p1']}|true",
            "{'projects': ['p2']}|false",
            "{}|true",
            "{'projects': null}|true",
            "{'tags': ['t1']}|true"
    })
    @DisplayName("Keeps a rule without projects or listing an included project")
    void filtersRulesByIncludedProjects(String metadata, boolean expected) {
        // Given
        PayloadFilter filter = new PayloadFilter(PayloadFilters.builder().projects(Collections.singletonList("p1")).build(), null);

        // When
        boolean inProjects = filter.ruleInProjects(json(metadata));

        // Then
        assertEquals(expected, inProjects);
    }

    @Test
    @DisplayName("Keeps a rule without metadata")
    void keepsRuleWithoutMetadata() {
        // Given
        PayloadFilter filter = new PayloadFilter(PayloadFilters.builder().projects(Collections.singletonList("p1")).build(), null);

        // When
        boolean absent = filter.ruleInProjects(null);
        boolean notAnObject = filter.ruleInProjects(JsonParser.parseString("\"p2\""));

        // Then
        assertTrue(absent);
        assertTrue(notAnObject);
    }

    @Test
    @DisplayName("Keeps every rule when no projects are included")
    void keepsEveryRuleWithoutIncludedProjects() {
        // Given
        PayloadFilter filter = new PayloadFilter(null, PayloadFilters.builder().projects(Collections.singletonList("p1")).build());

        // When
        boolean inProjects = filter.ruleInProjects(json("{'projects': ['p2']}"));

        // Then
        assertTrue(inProjects);
    }

    private static Map<String, List<String>> customFields(String key1, String value1, String key2, String value2) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put(key1, Collections.singletonList(value1));
        fields.put(key2, Collections.singletonList(value2));
        return fields;
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
