package growthbook.sdk.java.sandbox;

public interface GbCacheManager {
    void saveContent(String key, String data);
    String loadCache(String key);
    void clearCache();
}
