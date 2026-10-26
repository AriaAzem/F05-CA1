package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SteeringControllerTest {

    private static final double DELTA = 1e-9;
    private static final double MAX_CHANGE_PER_TICK_DEGREES = 60.0;

    private static final double CITY_SPEED_KMH = 50.0;
    private static final double HIGHWAY_SPEED_KMH = 130.0;
    private static final double SHARP_TURN_DEGREES = 300.0;

    private SteeringController steering;

    @BeforeEach
    void setUp() {
        steering = new SteeringController();
    }

    static Stream<Arguments> anglesReachedTickByTick() {
        return Stream.of(
                arguments(30.0, CITY_SPEED_KMH, new double[]{30.0, 30.0}),
                arguments(60.0, CITY_SPEED_KMH, new double[]{60.0}),
                arguments(150.0, CITY_SPEED_KMH, new double[]{60.0, 120.0, 150.0}),
                arguments(-150.0, CITY_SPEED_KMH, new double[]{-60.0, -120.0, -150.0}),
                arguments(200.0, HIGHWAY_SPEED_KMH, new double[]{60.0, 90.0, 90.0}),
                arguments(-200.0, HIGHWAY_SPEED_KMH, new double[]{-60.0, -90.0, -90.0})
        );
    }

    @ParameterizedTest(name = "request {0} deg at {1} km/h -> {2}")
    @MethodSource("anglesReachedTickByTick")
    void update_SameRequestEveryTick_MovesAtMostSixtyDegreesPerTick(double requestedAngle, double speedKmh,
                                                                     double[] expectedAngles) {
        double[] angles = new double[expectedAngles.length];
        for (int tick = 0; tick < angles.length; tick++) {
            angles[tick] = steering.update(requestedAngle, speedKmh);
        }

        assertArrayEquals(expectedAngles, angles, DELTA);
    }

    @ParameterizedTest(name = "request {0} deg at {1} km/h -> target {2}")
    @CsvSource({
            "1000,  50,  450",
            "450,   50,  450",
            "-1000, 50,  -450",
            "200,   0,   200",
            "200,   120, 200",
            "200,   121, 90",
            "90,    121, 90",
            "-200,  121, -90",
            "45,    130, 45"
    })
    void update_RequestedAngle_SetsTargetWithinLimitForSpeed(double requestedAngle, double speedKmh,
                                                              double expectedTarget) {
        steering.update(requestedAngle, speedKmh);

        assertEquals(expectedTarget, steering.getTargetAngleDegrees(), DELTA);
    }

    @Test
    void update_SpeedRisesWhileWheelTurnedPastHighSpeedLimit_TurnsBackTowardLimit() {
        turnWheelTo(SHARP_TURN_DEGREES);

        double angle = steering.update(SHARP_TURN_DEGREES, HIGHWAY_SPEED_KMH);

        assertEquals(SHARP_TURN_DEGREES - MAX_CHANGE_PER_TICK_DEGREES, angle, DELTA);
    }

    @ParameterizedTest(name = "angle {0}")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_NonFiniteAngle_ThrowsIllegalArgument(double requestedAngle) {
        assertThrows(IllegalArgumentException.class, () -> steering.update(requestedAngle, CITY_SPEED_KMH));
    }

    @ParameterizedTest(name = "speed {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_InvalidSpeed_ThrowsIllegalArgument(double speedKmh) {
        assertThrows(IllegalArgumentException.class, () -> steering.update(0.0, speedKmh));
    }

    @ParameterizedTest(name = "wheel at {0} deg -> {1} deg after one tick")
    @CsvSource({
            "0,    0",
            "40,   0",
            "-60,  0",
            "150,  90",
            "-150, -90"
    })
    void selfCenter_OneTick_MovesWheelTowardZero(double startAngle, double expectedAngle) {
        turnWheelTo(startAngle);

        double angle = steering.selfCenter();

        assertEquals(expectedAngle, angle, DELTA);
    }

    private void turnWheelTo(double angle) {
        int ticks = (int) Math.ceil(Math.abs(angle) / MAX_CHANGE_PER_TICK_DEGREES);
        for (int tick = 0; tick < ticks; tick++) {
            steering.update(angle, CITY_SPEED_KMH);
        }
    }
}
