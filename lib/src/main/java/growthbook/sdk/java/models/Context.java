package growthbook.sdk.java.models;

public interface Context {
    Boolean getEnabled();
    void setEnabled(Boolean isEnabled);

    String getUrl();
    void setUrl(String url);

    Boolean getIsQaMode();
    void setIsQaMode(Boolean isQaMode);

    // TODO: TrackingCallback
    // TODO: Attributes
    // TODO: Features
    // TODO: ForcedVariations
}
