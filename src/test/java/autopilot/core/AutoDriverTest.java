package autopilot.core;

import autopilot.core.AdaptiveCruise.Mode;
import autopilot.core.AutoDriver.DriveResult;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SensorSnapshot;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AutoDriverTest {

    private static final double DELTA = 1e-9;
    private static final double TARGET_SPEED_KMH = 80.0;
    private static final double FAR_OBSTACLE_METERS = 100.0;
    private static final double CENTERED_IN_LANE = 0.0;
    private static final double SAFE_MODE_BRAKE_PRESSURE = 30.0;

    private static final SensorSnapshot WORKING_SENSORS =
            SensorSnapshot.of(TARGET_SPEED_KMH, FAR_OBSTACLE_METERS, CENTERED_IN_LANE);
    private static final SensorSnapshot BROKEN_SENSORS = SensorSnapshot.failed("sensor offline");

    @Test
    void tick_ClearRoadAtTargetSpeed_HoldsSpeedWithoutBrakingOrSteering() {
        AutoDriver driver = driverWith(WORKING_SENSORS, WORKING_SENSORS, WORKING_SENSORS);

        DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(new DriveResult(0.0, 0.0, Mode.HOLD, false, false), result);
    }

    @ParameterizedTest(name = "lane offset {0} m -> steering {1} deg")
    @CsvSource({
            "1.0,  -45",
            "0.51, -45",
            "0.5,  0",
            "-0.5, 0",
            "-0.51, 45",
            "-1.0, 45"
    })
    void tick_LaneOffset_SteersBackOnlyWhenBeyondThreshold(double laneOffsetMeters, double expectedAngle) {
        SensorSnapshot sensors = SensorSnapshot.of(TARGET_SPEED_KMH, FAR_OBSTACLE_METERS, laneOffsetMeters);
        AutoDriver driver = driverWith(sensors, sensors, sensors);

        DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(expectedAngle, result.steeringAngleDegrees(), DELTA);
    }

    static Stream<Arguments> oneFailingSensor() {
        return Stream.of(
                arguments(named("speed sensor fails", BROKEN_SENSORS), WORKING_SENSORS, WORKING_SENSORS),
                arguments(named("obstacle sensor fails", WORKING_SENSORS), BROKEN_SENSORS, WORKING_SENSORS),
                arguments(named("lane sensor fails", WORKING_SENSORS), WORKING_SENSORS, BROKEN_SENSORS)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneFailingSensor")
    void tick_SensorFailure_EntersSafeModeWithGentleBraking(SpeedSensor speedSensor,
                                                            ObstacleSensor obstacleSensor,
                                                            LaneSensor laneSensor) {
        AutoDriver driver = driverWith(speedSensor, obstacleSensor, laneSensor);

        DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(new DriveResult(0.0, SAFE_MODE_BRAKE_PRESSURE, Mode.BRAKE, false, true), result);
    }

    private AutoDriver driverWith(SpeedSensor speedSensor, ObstacleSensor obstacleSensor, LaneSensor laneSensor) {
        return new AutoDriver(speedSensor, obstacleSensor, laneSensor, TARGET_SPEED_KMH);
    }
}
