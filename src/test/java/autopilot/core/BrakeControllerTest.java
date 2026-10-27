package autopilot.core;

import autopilot.core.GearBox.Gear;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrakeControllerTest {

    private static final double DELTA = 1e-9;
    private static final double MAX_PRESSURE = 100.0;
    private static final double ABS_HIGH_PULSE = 90.0;
    private static final double ABS_LOW_PULSE = 60.0;

    private static final double HIGHWAY_SPEED_KMH = 100.0;
    private static final double HARD_BRAKE_PRESSURE = 95.0;
    private static final double LIGHT_BRAKE_PRESSURE = 40.0;
    private static final double INVALID_PRESSURE = -1.0;

    private BrakeController brakeController;

    @BeforeEach
    void setUp() {
        brakeController = new BrakeController();
    }

    @ParameterizedTest(name = "requested {0}% at {1} km/h -> {2}%")
    @CsvSource({
            "0,   0,   0",
            "50,  100, 50",
            "80,  100, 80",
            "95,  30,  95",
            "100, 20,  100",
            "150, 20,  100",
            "81,  31,  90",
            "150, 100, 90"
    })
    void apply_SingleRequest_ReturnsExpectedPressure(double requestedPressure, double speedKmh,
                                                     double expectedPressure) {
        double pressure = brakeController.apply(requestedPressure, speedKmh, Gear.D);

        assertEquals(expectedPressure, pressure, DELTA);
    }

    @Test
    void apply_RepeatedHardBrakingAtSpeed_AlternatesAbsPulses() {
        double[] pressures = {
                brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D),
                brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D),
                brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D)
        };

        assertArrayEquals(new double[]{ABS_HIGH_PULSE, ABS_LOW_PULSE, ABS_HIGH_PULSE}, pressures, DELTA);
    }

    @Test
    void apply_LightBrakingBetweenAbsPulses_RestartsAbsWithHighPulse() {
        brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);
        brakeController.apply(LIGHT_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);

        double pressure = brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);

        assertEquals(ABS_HIGH_PULSE, pressure, DELTA);
    }

    @Test
    void apply_HardBraking_DoesNotActivateWarning() {
        brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);

        assertFalse(brakeController.isWarningActive());
    }

    @Test
    void apply_AfterEmergencyBrake_KeepsWarningActive() {
        brakeController.emergencyBrake(Gear.D);

        brakeController.apply(LIGHT_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);

        assertTrue(brakeController.isWarningActive());
    }

    @ParameterizedTest(name = "pressure {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void apply_InvalidPressure_ThrowsIllegalArgument(double requestedPressure) {
        assertThrows(IllegalArgumentException.class,
                () -> brakeController.apply(requestedPressure, HIGHWAY_SPEED_KMH, Gear.D));
    }

    @ParameterizedTest(name = "speed {0}")
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void apply_InvalidSpeed_ThrowsIllegalArgument(double speedKmh) {
        assertThrows(IllegalArgumentException.class,
                () -> brakeController.apply(LIGHT_BRAKE_PRESSURE, speedKmh, Gear.D));
    }

    @Test
    void apply_InvalidRequest_KeepsPreviousPressure() {
        brakeController.apply(LIGHT_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);

        assertThrows(IllegalArgumentException.class,
                () -> brakeController.apply(INVALID_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D));

        assertEquals(LIGHT_BRAKE_PRESSURE, brakeController.getPressure(), DELTA);
    }

    @Test
    void apply_GearInPark_ThrowsIllegalState() {
        assertThrows(IllegalStateException.class,
                () -> brakeController.apply(LIGHT_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.P));
    }

    @Test
    void apply_NullGear_ThrowsNullPointer() {
        assertThrows(NullPointerException.class,
                () -> brakeController.apply(LIGHT_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, null));
    }

    @ParameterizedTest(name = "gear {0}")
    @EnumSource(value = Gear.class, names = "P", mode = EnumSource.Mode.EXCLUDE)
    void emergencyBrake_GearOtherThanPark_SetsMaximumPressure(Gear gear) {
        brakeController.emergencyBrake(gear);

        assertEquals(MAX_PRESSURE, brakeController.getPressure(), DELTA);
    }

    @ParameterizedTest(name = "gear {0}")
    @EnumSource(value = Gear.class, names = "P", mode = EnumSource.Mode.EXCLUDE)
    void emergencyBrake_GearOtherThanPark_ActivatesWarning(Gear gear) {
        brakeController.emergencyBrake(gear);

        assertTrue(brakeController.isWarningActive());
    }

    @Test
    void emergencyBrake_DuringAbsBraking_RestartsAbsWithHighPulse() {
        brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);
        brakeController.emergencyBrake(Gear.D);

        double pressure = brakeController.apply(HARD_BRAKE_PRESSURE, HIGHWAY_SPEED_KMH, Gear.D);

        assertEquals(ABS_HIGH_PULSE, pressure, DELTA);
    }

    @Test
    void emergencyBrake_GearInPark_ThrowsIllegalState() {
        assertThrows(IllegalStateException.class, () -> brakeController.emergencyBrake(Gear.P));
    }

    @Test
    void emergencyBrake_NullGear_ThrowsNullPointer() {
        assertThrows(NullPointerException.class, () -> brakeController.emergencyBrake(null));
    }
}
