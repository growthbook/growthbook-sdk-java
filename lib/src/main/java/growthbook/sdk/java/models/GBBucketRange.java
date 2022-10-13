package growthbook.sdk.java.models;

class GBBucketRange implements BucketRange {
    private final Float rangeStart;
    private final Float rangeEnd;

    public GBBucketRange(Float start, Float end) {
        this.rangeStart = start;
        this.rangeEnd = end;
    }

    @Override
    public Float getStart() {
        return this.rangeStart;
    }

    @Override
    public Float getEnd() {
        return this.rangeEnd;
    }
}
