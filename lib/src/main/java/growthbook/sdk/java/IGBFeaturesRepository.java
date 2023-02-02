package growthbook.sdk.java;

interface IGBFeaturesRepository {
    void initialize() throws FeatureFetchException;

    String getFeaturesJson();
}
