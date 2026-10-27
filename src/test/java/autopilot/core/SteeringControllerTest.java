package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SteeringControllerTest {

    private static final double CITY_SPEED_KMH = 50.0;
    private static final double HIGHWAY_SPEED_KMH = 130.0;
    private static final double SHARP_TURN_DEGREES = 300.0;
    // A full sweep from -450 to +450 degrees takes 15 ticks at 60 degrees per tick.
    private static final int MAX_SETUP_TICKS = 15;

    private SteeringController steering;

    @BeforeEach
    void setUp() {
        steering = new SteeringController();
    }

    static Stream<Arguments> anglesReachedTickByTick() {
        return Stream.of(
                arguments(30.0, CITY_SPEED_KMH, List.of(30.0, 30.0)),
                arguments(60.0, CITY_SPEED_KMH, List.of(60.0)),
                arguments(150.0, CITY_SPEED_KMH, List.of(60.0, 120.0, 150.0)),
                arguments(-150.0, CITY_SPEED_KMH, List.of(-60.0, -120.0, -150.0)),
                arguments(200.0, HIGHWAY_SPEED_KMH, List.of(60.0, 90.0, 90.0)),
                arguments(-200.0, HIGHWAY_SPEED_KMH, List.of(-60.0, -90.0, -90.0))
        );
    }

    @ParameterizedTest(name = "request {0} deg at {1} km/h -> {2}")
    @MethodSource("anglesReachedTickByTick")
    void update_SameRequestEveryTick_MovesAtMostSixtyDegreesPerTick(double requestedAngle, double speedKmh,
                                                                     List<Double> expectedAngles) {
        List<Double> angles = new ArrayList<>();
        for (int tick = 0; tick < expectedAngles.size(); tick++) {
            angles.add(steering.update(requestedAngle, speedKmh));
        }

        assertEquals(expectedAngles, angles);
    }

    @ParameterizedTest(name = "request {0} deg at {1} km/h -> target {2}")
    @CsvSource({
            "1000,  50,  450",
            "-1000, 50,  -450",
            "200,   120, 200",
            "200,   121, 90",
            "-200,  121, -90",
            "45,    130, 45"
    })
    void update_RequestOutsideAllowedRange_ClampsTargetAngle(double requestedAngle, double speedKmh,
                                                             double expectedTarget) {
        steering.update(requestedAngle, speedKmh);

        assertEquals(expectedTarget, steering.getTargetAngleDegrees());
    }

    @Test
    void update_SpeedRisesWhileWheelTurnedPastHighSpeedLimit_TurnsBackTowardLimit() {
        turnWheelTo(SHARP_TURN_DEGREES);

        double angle = steering.update(SHARP_TURN_DEGREES, HIGHWAY_SPEED_KMH);

        assertEquals(240.0, angle);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_NonFiniteAngle_ThrowsIllegalArgument(double requestedAngle) {
        assertThrows(IllegalArgumentException.class, () -> steering.update(requestedAngle, CITY_SPEED_KMH));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY})
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

        assertEquals(expectedAngle, angle);
    }

    @Test
    void selfCenter_RepeatedTicks_ReachesCenterAndStays() {
        turnWheelTo(150.0);

        steering.selfCenter();
        steering.selfCenter();
        steering.selfCenter();
        double angle = steering.selfCenter();

        assertEquals(0.0, angle);
    }

    private void turnWheelTo(double angle) {
        for (int tick = 0; tick < MAX_SETUP_TICKS && steering.getCurrentAngleDegrees() != angle; tick++) {
            steering.update(angle, CITY_SPEED_KMH);
        }
        assertEquals(angle, steering.getCurrentAngleDegrees(),
                "Steering setup did not reach the requested angle within " + MAX_SETUP_TICKS + " ticks");
    }
}
