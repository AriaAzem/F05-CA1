package autopilot.core;

import autopilot.core.AdaptiveCruise.Decision;
import autopilot.core.AdaptiveCruise.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdaptiveCruiseTest {

    private static final double TARGET_SPEED_KMH = 100.0;

    // At 80 km/h: safe distance = 40 m, emergency distance = 20 m, release distance = 48 m.
    private static final double SPEED_KMH = 80.0;
    private static final double TOO_CLOSE_METERS = 30.0;
    private static final double INSIDE_HYSTERESIS_BAND_METERS = 45.0;
    private static final double RELEASE_DISTANCE_METERS = 48.0;
    private static final double MAX_ACCELERATION_STEP_KMH = 5.0;

    private AdaptiveCruise cruise;

    @BeforeEach
    void setUp() {
        cruise = new AdaptiveCruise(TARGET_SPEED_KMH);
    }

    @ParameterizedTest(name = "{0} km/h -> {1} m")
    @CsvSource({
            "0,    0",
            "1,    0.5",
            "37.5, 18.75",
            "120,  60"
    })
    void safeDistanceFor_ValidSpeed_ReturnsHalfOfSpeedInMeters(double speedKmh, double expectedMeters) {
        assertEquals(expectedMeters, cruise.safeDistanceFor(speedKmh));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void safeDistanceFor_InvalidSpeed_ThrowsIllegalArgument(double speedKmh) {
        assertThrows(IllegalArgumentException.class, () -> cruise.safeDistanceFor(speedKmh));
    }

    @ParameterizedTest(name = "{0} km/h, {1} m ahead -> {2} {3}")
    @CsvSource({
            "80,  0,   EMERGENCY_BRAKE, 100",
            "80,  20,  EMERGENCY_BRAKE, 100",
            "80,  30,  BRAKE,           25",
            "80,  37.5, BRAKE,          6.25",
            "80,  40,  HOLD,            0",
            "80,  45,  ACCELERATE,      5",
            "98,  100, ACCELERATE,      2",
            "100, 100, HOLD,            0",
            "120, 100, HOLD,            0",
            "0,   0,   EMERGENCY_BRAKE, 100",
            "0,   10,  ACCELERATE,      5"
    })
    void update_FirstReading_ReturnsDecisionForDistance(double speedKmh, double distanceMeters,
                                                         Mode expectedMode, double expectedAmount) {
        Decision decision = cruise.update(speedKmh, distanceMeters);

        assertEquals(new Decision(expectedMode, expectedAmount), decision);
    }

    @ParameterizedTest(name = "{0} m then {1} m -> {2} {3}")
    @CsvSource({
            "30, 45, BRAKE,      10",
            "20, 45, BRAKE,      10",
            "30, 35, BRAKE,      12.5",
            "30, 40, BRAKE,      10",
            "30, 48, ACCELERATE, 5"
    })
    void update_AfterBraking_KeepsBrakingUntilReleaseDistance(double firstDistance, double secondDistance,
                                                              Mode expectedMode, double expectedAmount) {
        cruise.update(SPEED_KMH, firstDistance);

        Decision decision = cruise.update(SPEED_KMH, secondDistance);

        assertEquals(new Decision(expectedMode, expectedAmount), decision);
    }

    @Test
    void update_AfterBrakingWasReleased_NoLongerAppliesHysteresis() {
        cruise.update(SPEED_KMH, TOO_CLOSE_METERS);
        cruise.update(SPEED_KMH, RELEASE_DISTANCE_METERS);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(new Decision(Mode.ACCELERATE, MAX_ACCELERATION_STEP_KMH), decision);
    }

    @ParameterizedTest
    @CsvSource({
            "-1,       10",
            "NaN,      10",
            "80,       -1",
            "80,       NaN",
            "80,       Infinity"
    })
    void update_InvalidSpeedOrDistance_ThrowsIllegalArgument(double speedKmh, double distanceMeters) {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(speedKmh, distanceMeters));
    }
}
