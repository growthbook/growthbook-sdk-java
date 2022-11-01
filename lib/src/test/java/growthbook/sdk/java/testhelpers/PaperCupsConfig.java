package growthbook.sdk.java.testhelpers;

public class PaperCupsConfig {
    public String token;
    public String title;
    public Boolean showAgentAvailability;

    public PaperCupsConfig(String token, String title, Boolean showAgentAvailability) {
        this.token = token;
        this.title = title;
        this.showAgentAvailability = showAgentAvailability;
    }
}
