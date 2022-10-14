package growthbook.sdk.java.models;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackingCallbackTest {

    // Note: Using the Lombok auto-generated builder with generics
    TrackingResult<String> trackingResult = TrackingResult
            .<String>builder()
            .value("hello")
            .inExperiment(true)
            .build();
//    TrackingResult<String> trackingResult = new TrackingResult<String>("hello", 13, true);

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void onTrack() {
    }
}