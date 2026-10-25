package autopilot.core;

import autopilot.core.GearBox.Gear;
import autopilot.exception.IllegalGearChangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GearBoxTest {

    private static final double STOPPED = 0.0;
    private static final double INVALID_SPEED = -1.0;

    private double sensorSpeedKmh;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        sensorSpeedKmh = STOPPED;
        gearBox = new GearBox(() -> sensorSpeedKmh);
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvFileSource(resources = "/gearbox/allowed-gear-changes.csv", numLinesToSkip = 1)
    void changeTo_AllowedTransition_SwitchesToTargetGear(Gear from, Gear to, double speedKmh) {
        shiftFromParkTo(from);
        sensorSpeedKmh = speedKmh;

        gearBox.changeTo(to);

        assertEquals(to, gearBox.getCurrentGear());
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvFileSource(resources = "/gearbox/rejected-gear-changes.csv", numLinesToSkip = 1)
    void changeTo_ForbiddenTransition_ThrowsIllegalGearChange(Gear from, Gear to, double speedKmh) {
        shiftFromParkTo(from);
        sensorSpeedKmh = speedKmh;

        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(to));
    }

    @Test
    void changeTo_ForbiddenTransition_KeepsCurrentGear() {
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(Gear.D));

        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @Test
    void changeTo_SameGearAsCurrent_DoesNotReadSpeed() {
        sensorSpeedKmh = INVALID_SPEED;

        gearBox.changeTo(Gear.P);

        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void changeTo_InvalidSpeedReading_ThrowsIllegalArgument(double speedKmh) {
        sensorSpeedKmh = speedKmh;

        assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(Gear.R));
    }

    @Test
    void changeTo_NullTarget_ThrowsNullPointer() {
        assertThrows(NullPointerException.class, () -> gearBox.changeTo(null));
    }

    // The gearbox always starts in P, so the only way to reach another gear is through R (and then N).
    private void shiftFromParkTo(Gear gear) {
        sensorSpeedKmh = STOPPED;
        switch (gear) {
            case P -> { }
            case R -> gearBox.changeTo(Gear.R);
            case N -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.N);
            }
            case D -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.D);
            }
        }
    }
}
