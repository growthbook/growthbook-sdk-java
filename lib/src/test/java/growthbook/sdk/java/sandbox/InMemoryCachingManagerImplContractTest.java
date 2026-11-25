package growthbook.sdk.java.sandbox;

class InMemoryCachingManagerImplContractTest extends GbCacheManagerContractTest {

    @Override
    protected GbCacheManager createCacheManager() {
        return new InMemoryCachingManagerImpl(clock());
    }
}
