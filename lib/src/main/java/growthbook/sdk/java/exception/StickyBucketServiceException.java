package growthbook.sdk.java.exception;

/**
 * Thrown when a configured {@link growthbook.sdk.java.stickyBucketing.StickyBucketService} backend
 * cannot be accessed or returns a payload that cannot be parsed.
 *
 * <p>Backends that talk to a network store (e.g. Redis) report genuine access failures by throwing
 * this exception rather than silently swallowing them. Whether such a failure should degrade to
 * fresh bucketing or abort the request is a policy decision for the caller, mirroring how
 * {@link FeatureCacheException} is handled at the repository layer.
 */
public class StickyBucketServiceException extends RuntimeException {
    public StickyBucketServiceException(String message) {
        super(message);
    }

    public StickyBucketServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
