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

    private static final double DELTA = 1e-9;
    private static final double TARGET_SPEED_KMH = 100.0;
    private static final double FULL_BRAKE_PRESSURE = 100.0;
    private static final double MAX_ACCELERATION_STEP_KMH = 5.0;
    private static final double SPEED_KMH = 80.0;
    private static final double TOO_CLOSE_METERS = 30.0;
    private static final double INSIDE_HYSTERESIS_BAND_METERS = 45.0;
    private static final double RELEASE_DISTANCE_METERS = 48.0;

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
        assertEquals(expectedMeters, cruise.safeDistanceFor(speedKmh), DELTA);
    }

    @ParameterizedTest(name = "speed {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void safeDistanceFor_InvalidSpeed_ThrowsIllegalArgument(double speedKmh) {
        assertThrows(IllegalArgumentException.class, () -> cruise.safeDistanceFor(speedKmh));
    }

    @ParameterizedTest(name = "{0} km/h, {1} m ahead")
    @CsvSource({
            "80, 0",
            "80, 20",
            "0,  0"
    })
    void update_ObstacleWithinEmergencyDistance_BrakesWithFullPressure(double speedKmh, double distanceMeters) {
        Decision decision = cruise.update(speedKmh, distanceMeters);

        assertDecision(Mode.EMERGENCY_BRAKE, FULL_BRAKE_PRESSURE, decision);
    }

    @ParameterizedTest(name = "{0} km/h, {1} m ahead -> brake {2}%")
    @CsvSource({
            "80, 20.4, 49",
            "80, 30,   25",
            "80, 37.5, 6.25"
    })
    void update_ObstacleCloserThanSafeDistance_BrakesProportionally(double speedKmh, double distanceMeters,
                                                                    double expectedPressure) {
        Decision decision = cruise.update(speedKmh, distanceMeters);

        assertDecision(Mode.BRAKE, expectedPressure, decision);
    }

    @ParameterizedTest(name = "{0} km/h, {1} m ahead -> +{2} km/h")
    @CsvSource({
            "80, 45,  5",
            "95, 100, 5",
            "98, 100, 2",
            "0,  10,  5"
    })
    void update_FartherThanSafeDistanceBelowTargetSpeed_Accelerates(double speedKmh, double distanceMeters,
                                                                     double expectedStep) {
        Decision decision = cruise.update(speedKmh, distanceMeters);

        assertDecision(Mode.ACCELERATE, expectedStep, decision);
    }

    @ParameterizedTest(name = "{0} km/h, {1} m ahead")
    @CsvSource({
            "80,  40",
            "100, 100",
            "120, 100"
    })
    void update_AtSafeDistanceOrNotBelowTargetSpeed_HoldsSpeed(double speedKmh, double distanceMeters) {
        Decision decision = cruise.update(speedKmh, distanceMeters);

        assertDecision(Mode.HOLD, 0.0, decision);
    }

    @ParameterizedTest(name = "{0} m then {1} m -> {2} {3}")
    @CsvSource({
            "30, 45, BRAKE,           10",
            "20, 45, BRAKE,           10",
            "30, 40, BRAKE,           10",
            "30, 35, BRAKE,           12.5",
            "30, 15, EMERGENCY_BRAKE, 100",
            "30, 48, ACCELERATE,      5"
    })
    void update_AfterBraking_KeepsBrakingUntilReleaseDistance(double firstDistance, double secondDistance,
                                                              Mode expectedMode, double expectedAmount) {
        cruise.update(SPEED_KMH, firstDistance);

        Decision decision = cruise.update(SPEED_KMH, secondDistance);

        assertDecision(expectedMode, expectedAmount, decision);
    }

    @Test
    void update_AfterBrakingWasReleased_NoLongerAppliesHysteresis() {
        cruise.update(SPEED_KMH, TOO_CLOSE_METERS);
        cruise.update(SPEED_KMH, RELEASE_DISTANCE_METERS);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertDecision(Mode.ACCELERATE, MAX_ACCELERATION_STEP_KMH, decision);
    }

    @ParameterizedTest(name = "speed {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_InvalidSpeed_ThrowsIllegalArgument(double speedKmh) {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(speedKmh, TOO_CLOSE_METERS));
    }

    @ParameterizedTest(name = "distance {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_InvalidDistance_ThrowsIllegalArgument(double distanceMeters) {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(SPEED_KMH, distanceMeters));
    }

    private void assertDecision(Mode expectedMode, double expectedAmount, Decision decision) {
        assertEquals(expectedMode, decision.mode());
        assertEquals(expectedAmount, decision.amount(), DELTA);
    }
}
