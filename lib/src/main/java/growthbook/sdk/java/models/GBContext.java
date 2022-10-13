package growthbook.sdk.java.models;

import javax.annotation.Nullable;

public class GBContext implements Context {

    private Boolean isEnabled = true;
    private Boolean isQaMode = false;

    @Nullable
    private String url = null;

    public GBContext(Boolean isEnabled, String url, Boolean isQaMode) {
        this.isEnabled = isEnabled;
        this.url = url;
        this.isQaMode = isQaMode;
    }

    @Override
    public Boolean getEnabled() {
        return this.isEnabled;
    }

    @Override
    public void setEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Nullable
    @Override
    public String getUrl() {
        return this.url;
    }

    @Override
    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    public Boolean getIsQaMode() {
        return this.isQaMode;
    }

    @Override
    public void setIsQaMode(Boolean isQaMode) {
        this.isQaMode = isQaMode;
    }
}
