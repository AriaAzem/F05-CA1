# F05-CA1: Autonomous Vehicle Project

## Architecture

The project architecture is divided into three main packages:

- `sensor`: Defines interfaces for speed, obstacle distance, and lane deviation sensors. `SensorSnapshot` stores real-time sensor readings at a specific moment and can report sensor failures.
- `core`: Contains the main vehicle control logic.
- `exception`: Defines exceptions related to invalid gear shifts and sensor failures.

`AutoDriver` acts as the main coordinator. It reads sensor data, manages steering, adaptive cruise control, and braking, and switches to a safe mode in case of sensor failure.

## Class Logic

- **`GearBox`**: Implements a state machine for the `P`, `R`, `N`, and `D` gear positions. Gear transitions depend on the vehicle's current speed, and invalid transitions throw exceptions.
- **`SteeringController`**: Maintains the steering angle within `-450°` to `+450°` and limits changes to `60°` per tick. Above `120 km/h`, the target angle is restricted to `±90°`. If the wheel is already outside that range when speed increases, its current angle moves toward the restricted target over subsequent ticks. It also supports automatic steering centering.
- **`BrakeController`**: Manages brake pressure, caps it at `100%`, simulates ABS using alternating `60%` and `90%` pressure pulses, and supports emergency braking.
- **`AdaptiveCruise`**: Calculates the safe following distance as half the vehicle's speed in km/h, expressed in meters. Depending on the distance to the vehicle ahead, it gradually accelerates, maintains speed, brakes, or applies emergency braking. Hysteresis prevents rapid switching between acceleration and braking.
- **`AutoDriver`**: Corrects lane deviations greater than `0.5 meters` through steering adjustments. In case of sensor failure, it returns a safe-mode result with a centered steering command and `30%` brake pressure.

## Running the Tests

Use Maven and the course-required Eclipse Temurin JDK 26. The supplied `pom.xml` targets Java 17 bytecode; selecting a newer JDK does not require changing that target.

From the project root, run all tests:

```sh
mvn test
```

To run one test class:

```sh
mvn -Dtest=GearBoxTest test
```

The first Maven run needs access to Maven Central to download dependencies. Reports are written to `target/surefire-reports`.

In IntelliJ IDEA, open the project as a Maven project, select the project JDK, and sync Maven dependencies. Click the Run icon beside a test class or method, or right-click `src/test/java` and run all tests. You can also run Maven's `Lifecycle > test` goal from the Maven tool window.

## Continuous Integration

The [Java tests workflow](.github/workflows/tests.yml) runs all tests automatically on every push and pull request using Eclipse Temurin JDK 26 and Maven. Maven dependencies are cached to speed up subsequent runs. A failed test fails the CI job.

Open the repository's [Actions tab](https://github.com/AriaAzem/F05-CA1/actions/workflows/tests.yml) and select a run to see the results under **Run all tests**. You can also start a run manually with **Run workflow**. The workflow follows GitHub's [Java with Maven guide](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven).

## Test Design

Tests are under `src/test/java/autopilot/core`, with one file per class. Test method names use `method_Situation_ExpectedResult`. Each test invocation creates a fresh controller or driver so that cases do not depend on execution order.

The suite checks normal behavior, invalid inputs, boundary values, and state changes across multiple ticks. Examples include ABS pulse sequences, steering centering, and cruise-control braking hysteresis. At `80 km/h`, the safe distance is `40 m`: a first reading at that distance holds speed, but after braking has started, a reading at the same distance keeps `10%` braking until the release distance of `48 m` is reached.

The required parameter sources exercise different application methods:

| Source | Example method under test | Purpose |
| --- | --- | --- |
| `ValueSource` | `AdaptiveCruise.safeDistanceFor` | Negative and non-finite speeds |
| `CsvSource` | `BrakeController.apply` | Pressure, speed, and expected-pressure combinations |
| `CsvFileSource` | `GearBox.changeTo` | Allowed and rejected transitions from CSV resources |
| `EnumSource` | `BrakeController.emergencyBrake` | Each gear other than Park |
| `MethodSource` | `SteeringController.update` | Expected steering sequences over multiple ticks |

Same-gear requests are checked for all four gears using a speed sensor that fails if read. Steering setup uses a bounded number of ticks and checks that the requested starting angle was reached, so a regression fails the test instead of leaving an endless loop.

## Refactoring the Supplied AutoDriver Tests

The supplied tests had vague names such as `test1`, repeated sensor setup, duplicated lane and sensor-failure cases, and unexplained fixture values. The revised tests use descriptive names, named fixture constants, `SensorSnapshot` test doubles, and parameterized cases. Sensor-failure cases have a separate scenario label to identify the failing sensor in test results. Related output fields are checked together by comparing the expected result record.
