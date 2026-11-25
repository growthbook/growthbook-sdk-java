package growthbook.sdk.java.testhelpers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that only moves when a test advances it. Thread-safe.
 */
public final class TestClock extends Clock {

    private volatile Instant now;

    public TestClock(Instant start) {
        this.now = start;
    }

    public static TestClock at(String isoInstant) {
        return new TestClock(Instant.parse(isoInstant));
    }

    public static TestClock atMillis(long epochMillis) {
        return new TestClock(Instant.ofEpochMilli(epochMillis));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void advanceMillis(long millis) {
        now = now.plusMillis(millis);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
