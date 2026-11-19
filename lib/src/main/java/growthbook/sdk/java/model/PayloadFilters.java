package growthbook.sdk.java.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Filters on the payload metadata of features and auto experiments, used by
 * {@link ReducePayloadOptions}. A filter that is {@code null} is not applied; a value list
 * matches when any one of its values matches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayloadFilters {
    /**
     * Project ids. A feature or experiment in all projects matches any list.
     */
    @Nullable
    private List<String> projects;

    /**
     * Tags.
     */
    @Nullable
    private List<String> tags;

    /**
     * Custom field values by custom field id.
     */
    @Nullable
    private Map<String, List<String>> customFields;
}
