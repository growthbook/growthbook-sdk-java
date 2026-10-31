package growthbook.sdk.java.cache.caffeine;

import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.sandbox.GbCacheManagerContractTest;

class CaffeineGbCacheManagerContractTest extends GbCacheManagerContractTest {

    @Override
    protected GbCacheManager createCacheManager() {
        return CaffeineGbCacheManager.create();
    }
}
