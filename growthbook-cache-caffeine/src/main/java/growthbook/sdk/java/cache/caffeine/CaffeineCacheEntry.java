package growthbook.sdk.java.cache.caffeine;

final class CaffeineCacheEntry {

    private final String data;
    private final long lastUpdatedMillis;

    CaffeineCacheEntry(String data, long lastUpdatedMillis) {
        this.data = data;
        this.lastUpdatedMillis = lastUpdatedMillis;
    }

    String getData() {
        return data;
    }

    long getLastUpdatedMillis() {
        return lastUpdatedMillis;
    }
}
