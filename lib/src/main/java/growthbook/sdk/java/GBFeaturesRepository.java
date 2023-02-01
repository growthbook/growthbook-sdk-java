package growthbook.sdk.java;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder
public class GBFeaturesRepository implements IGBFeaturesRepository {

    @Nullable
    private String endpoint;

    @Nullable
    private String encryptionKey;

    @Override
    public void initialize() {
    }

    @Override
    public String getFeaturesJson() {
        return "{}";
    }
}
