package edu.physics.problem;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static edu.physics.problem.ProjectileProblem.Input.*;
import static edu.physics.problem.ProjectileProblem.Target.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileProblemSolverTest {
    private static final double TOL = 1e-8;
    private final ProjectileProblemSolver solver = new ProjectileProblemSolver();

    @Test
    void solvesCommonEventsAndReturnsStructuredTrace() {
        ProjectileProblem rangeProblem = problem(HORIZONTAL_RANGE,
                Map.of(INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                        LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                        GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)));
        ProjectileSolution range = solver.solve(rangeProblem);
        assertEquals(ProjectileSolution.Status.SOLVED, range.status());
        assertEquals(400.0 / 9.8, range.candidates().get(0).get("range").valueInSi(), TOL);
        assertFalse(range.steps().isEmpty());
        assertFalse(range.steps().get(0).equationId().isBlank());
        assertTrue(range.assumptions().contains("initial-y-defaulted-to-zero-metres"));

        ProjectileSolution max = solver.solve(problem(MAXIMUM_HEIGHT, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))));
        assertEquals(100.0 / 9.8, max.candidates().get(0).get("maximumHeight").valueInSi(), TOL);
    }

    @Test
    void acceptsCompatibleUnitsAndRejectsIncompatibleUnits() {
        ProjectileSolution converted = solver.solve(problem(SPEED_AT_TIME, Map.of(
                INITIAL_SPEED, q(36, PhysicalUnit.KILOMETRE_PER_HOUR),
                LAUNCH_ANGLE, q(Math.PI / 2, PhysicalUnit.RADIAN),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(0, PhysicalUnit.MILLISECOND))));
        assertEquals(ProjectileSolution.Status.SOLVED, converted.status());
        assertEquals(10, converted.candidates().get(0).get("speed").valueInSi(), TOL);

        ProjectileSolution invalid = solver.solve(problem(SPEED_AT_TIME, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(1, PhysicalUnit.SECOND))));
        assertEquals(ProjectileSolution.Status.INVALID_INPUT, invalid.status());
        assertTrue(invalid.warnings().contains("incompatible-unit:INITIAL_SPEED"));
    }

    @Test
    void preservesBothSolutionsForHeightAndReportsNoSolution() {
        Map<ProjectileProblem.Input, PhysicalQuantity> common = Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(90, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_Y, q(15, PhysicalUnit.METRE));
        ProjectileSolution multiple = solver.solve(problem(TIMES_AT_HEIGHT, common));
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, multiple.status());
        assertEquals(1, multiple.candidates().get(0).get("time").valueInSi(), TOL);
        assertEquals(3, multiple.candidates().get(1).get("time").valueInSi(), TOL);

        ProjectileProblem unreachable = problem(SPEED_AT_HEIGHT, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(90, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_Y, q(6, PhysicalUnit.METRE)));
        assertEquals(ProjectileSolution.Status.NO_SOLUTION, solver.solve(unreachable).status());
    }

    @Test
    void reportsUnderdeterminedAndNonFiniteInputsStructurally() {
        ProjectileSolution missing = solver.solve(problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))));
        assertEquals(ProjectileSolution.Status.UNDERDETERMINED, missing.status());
        assertFalse(missing.missingInputs().isEmpty());

        ProjectileSolution nonFinite = solver.solve(problem(MAXIMUM_HEIGHT, Map.of(
                INITIAL_SPEED, q(Double.NaN, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))));
        assertEquals(ProjectileSolution.Status.INVALID_INPUT, nonFinite.status());
    }

    @Test
    void inverseSpeedAndAngleHandleUniqueMultipleAndImpossibleConditions() {
        ProjectileProblem speedProblem = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(10, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE)));
        assertEquals(Math.sqrt(98), solver.solve(speedProblem).candidates().get(0).get("initialSpeed").valueInSi(), TOL);

        ProjectileProblem angleProblem = problem(FIND_LAUNCH_ANGLE, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(20, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE)));
        ProjectileSolution angles = solver.solve(angleProblem);
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, angles.status());
        double lowAngle = Math.toDegrees(Math.asin(9.8 * 20 / (20.0 * 20.0))) / 2.0;
        assertEquals(lowAngle, angles.candidates().get(0).get("launchAngle").valueInSi(), TOL);
        assertEquals(90.0 - lowAngle, angles.candidates().get(1).get("launchAngle").valueInSi(), TOL);

        ProjectileProblem impossible = problem(FIND_LAUNCH_ANGLE, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(60, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE)));
        assertEquals(ProjectileSolution.Status.NO_SOLUTION, solver.solve(impossible).status());
    }

    @Test
    void derivesInitialSpeedFromVerticalVelocityAtSpecifiedTime() {
        ProjectileProblem problem = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(1, PhysicalUnit.SECOND),
                TARGET_VY, q(0, PhysicalUnit.METRE_PER_SECOND)));
        assertEquals(20, solver.solve(problem).candidates().get(0).get("initialSpeed").valueInSi(), TOL);
    }

    @Test
    void inverseInitialSpeedSupportsHorizontalVelocityAndSpeedConditions() {
        ProjectileProblem horizontalComponent = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(0, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_VX, q(12, PhysicalUnit.METRE_PER_SECOND)));
        assertEquals(12, value(solver.solve(horizontalComponent), "initialSpeed"), TOL);

        ProjectileProblem speedAtTime = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(1, PhysicalUnit.SECOND),
                TARGET_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND)));
        ProjectileSolution roots = solver.solve(speedAtTime);
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, roots.status());
        assertEquals(0, roots.candidates().get(0).get("initialSpeed").valueInSi(), TOL);
        assertEquals(10, roots.candidates().get(1).get("initialSpeed").valueInSi(), TOL);

        ProjectileSolution negativeSpeed = solver.solve(problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(1, PhysicalUnit.SECOND),
                TARGET_SPEED, q(-1, PhysicalUnit.METRE_PER_SECOND))));
        assertEquals(ProjectileSolution.Status.INVALID_INPUT, negativeSpeed.status());
    }

    @Test
    void arbitraryTimeQueriesUseTheSameAnalyticalModel() {
        ProjectileProblem velocity = problem(VELOCITY_AT_TIME, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(0.5, PhysicalUnit.SECOND)));
        ProjectileSolution result = solver.solve(velocity);
        assertEquals(5 * Math.sqrt(3), result.candidates().get(0).get("vx").valueInSi(), TOL);
        assertEquals(0, result.candidates().get(0).get("vy").valueInSi(), TOL);

        ProjectileSolution arc = solver.solve(problem(DISTANCE_TRAVELLED_AT_TIME, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(0, PhysicalUnit.DEGREE),
                GRAVITY, q(0, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(2, PhysicalUnit.SECOND))));
        assertEquals(20, arc.candidates().get(0).get("distanceTravelled").valueInSi(), TOL);
    }

    @Test
    void returnsPositionAndVelocityAtHorizontalAndVerticalCoordinates() {
        ProjectileProblem horizontalPosition = problem(POSITION_AT_HORIZONTAL_DISPLACEMENT, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(10, PhysicalUnit.METRE)));
        ProjectileSolution position = solver.solve(horizontalPosition);
        assertEquals(10, position.candidates().get(0).get("x").valueInSi(), TOL);
        assertEquals(7.5, position.candidates().get(0).get("y").valueInSi(), TOL);

        ProjectileProblem verticalVelocity = problem(VELOCITY_AT_HEIGHT, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(90, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_Y, q(15, PhysicalUnit.METRE)));
        ProjectileSolution velocities = solver.solve(verticalVelocity);
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, velocities.status());
        assertEquals(10, velocities.candidates().get(0).get("vy").valueInSi(), TOL);
        assertEquals(-10, velocities.candidates().get(1).get("vy").valueInSi(), TOL);
    }

    @Test
    void positionQueryPreservesMultipleTimesAndRejectsInconsistentCoordinates() {
        ProjectileSolution multiple = solver.solve(problem(TIMES_AT_POSITION, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(90, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_X, q(0, PhysicalUnit.METRE),
                TARGET_Y, q(15, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, multiple.status());
        assertEquals(1, multiple.candidates().get(0).get("time").valueInSi(), TOL);
        assertEquals(3, multiple.candidates().get(1).get("time").valueInSi(), TOL);

        ProjectileSolution inconsistent = solver.solve(problem(TIMES_AT_POSITION, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_X, q(10, PhysicalUnit.METRE),
                TARGET_Y, q(20, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.NO_SOLUTION, inconsistent.status());
    }

    @Test
    void solvesDependentMultipartQuestionsAndStopsAtAmbiguousDependency() {
        ProjectileProblemSequence sequence = new ProjectileProblemSequence("Find speed then confirm range", Map.of(
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)), java.util.List.of(
                new ProjectileProblemSequence.Part(FIND_INITIAL_SPEED, Map.of(
                        HORIZONTAL_DISPLACEMENT, q(10, PhysicalUnit.METRE),
                        VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE)), Map.of()),
                new ProjectileProblemSequence.Part(HORIZONTAL_RANGE, Map.of(), Map.of(
                        INITIAL_SPEED, new ProjectileProblemSequence.ResultReference(0, "initialSpeed")))));
        ProjectileSequenceSolution solved = solver.solveSequence(sequence);
        assertEquals(ProjectileSolution.Status.SOLVED, solved.status());
        assertEquals(2, solved.completedParts().size());
        assertEquals(10, solved.completedParts().get(1).solution().candidates().get(0).get("range").valueInSi(), TOL);

        ProjectileProblemSequence ambiguous = new ProjectileProblemSequence("Choose an angle branch first", Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED)), java.util.List.of(
                new ProjectileProblemSequence.Part(FIND_LAUNCH_ANGLE, Map.of(
                        HORIZONTAL_DISPLACEMENT, q(20, PhysicalUnit.METRE),
                        VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE)), Map.of()),
                new ProjectileProblemSequence.Part(FLIGHT_TIME, Map.of(), Map.of(
                        LAUNCH_ANGLE, new ProjectileProblemSequence.ResultReference(0, "launchAngle")))));
        ProjectileSequenceSolution stopped = solver.solveSequence(ambiguous);
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, stopped.status());
        assertEquals(0, stopped.unresolvedPartIndex());
        assertEquals(1, stopped.completedParts().size());
    }

    @Test
    void distinguishesAnUnboundedApexFromNoFiniteSolution() {
        ProjectileProblem unbounded = problem(MAXIMUM_HEIGHT, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(90, PhysicalUnit.DEGREE),
                GRAVITY, q(0, PhysicalUnit.METRE_PER_SECOND_SQUARED)));
        ProjectileSolution result = solver.solve(unbounded);
        assertEquals(ProjectileSolution.Status.UNBOUNDED, result.status());
        assertTrue(result.warnings().contains("positive-vertical-velocity-with-zero-gravity-has-no-finite-apex"));
    }

    @Test
    void everyForwardTargetReturnsTheRequestedPhysicalQuantity() {
        Map<ProjectileProblem.Input, PhysicalQuantity> base = Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                INITIAL_X, q(2, PhysicalUnit.METRE),
                INITIAL_Y, q(3, PhysicalUnit.METRE));
        ProjectileSolution apexTime = solver.solve(problem(TIME_TO_APEX, base));
        assertEquals(0.5, value(apexTime, "timeToApex"), TOL);
        assertEquals(4.25, value(solver.solve(problem(MAXIMUM_HEIGHT, base)), "maximumHeight"), TOL);
        assertEquals(5 * Math.sqrt(3), value(solver.solve(problem(SPEED_AT_APEX, base)), "speedAtApex"), TOL);

        Map<ProjectileProblem.Input, PhysicalQuantity> withTime = plus(base, TIME, q(1, PhysicalUnit.SECOND));
        ProjectileSolution positionAtTime = solver.solve(problem(POSITION_AT_TIME, withTime));
        assertEquals(2 + 5 * Math.sqrt(3), value(positionAtTime, "x"), TOL);
        assertEquals(3, value(positionAtTime, "y"), TOL);
        assertEquals(10, value(solver.solve(problem(SPEED_AT_TIME, withTime)), "speed"), TOL);

        double dx = 5 * Math.sqrt(3);
        Map<ProjectileProblem.Input, PhysicalQuantity> withX = plus(base, HORIZONTAL_DISPLACEMENT, q(dx, PhysicalUnit.METRE));
        assertEquals(1, value(solver.solve(problem(TIME_AT_HORIZONTAL_DISPLACEMENT, withX)), "time"), TOL);
        assertEquals(10, value(solver.solve(problem(SPEED_AT_HORIZONTAL_DISPLACEMENT, withX)), "speed"), TOL);
        assertEquals(-5, value(solver.solve(problem(VELOCITY_AT_HORIZONTAL_DISPLACEMENT, withX)), "vy"), TOL);

        Map<ProjectileProblem.Input, PhysicalQuantity> withHeight = plus(base, TARGET_Y, q(4, PhysicalUnit.METRE));
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, solver.solve(problem(POSITION_AT_HEIGHT, withHeight)).status());
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, solver.solve(problem(VELOCITY_AT_HEIGHT, withHeight)).status());
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, solver.solve(problem(SPEED_AT_HEIGHT, withHeight)).status());
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, solver.solve(problem(TIMES_AT_HEIGHT, withHeight)).status());

        ProjectileSolution range = solver.solve(problem(HORIZONTAL_RANGE, plus(base,
                LANDING_Y, q(0, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.SOLVED, range.status());
        assertEquals((5 * Math.sqrt(3)) * ((5 + Math.sqrt(85)) / 10), value(range, "range"), TOL);
        assertEquals((5 + Math.sqrt(85)) / 10, value(solver.solve(problem(FLIGHT_TIME,
                plus(base, LANDING_Y, q(0, PhysicalUnit.METRE)))), "flightTime"), TOL);
    }

    @Test
    void inverseSolversDistinguishRepeatedRootsAndImpossibleConditions() {
        ProjectileSolution repeatedAngle = solver.solve(problem(FIND_LAUNCH_ANGLE, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(400.0 / 9.8, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.SOLVED, repeatedAngle.status());
        assertEquals(45, value(repeatedAngle, "launchAngle"), TOL);

        ProjectileSolution impossibleSpeed = solver.solve(problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(10, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(100, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.NO_SOLUTION, impossibleSpeed.status());

        ProjectileSolution zeroSpeedAngle = solver.solve(problem(FIND_LAUNCH_ANGLE, Map.of(
                INITIAL_SPEED, q(0, PhysicalUnit.METRE_PER_SECOND),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(10, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.NO_SOLUTION, zeroSpeedAngle.status());
    }

    private static double value(ProjectileSolution solution, String key) {
        assertFalse(solution.candidates().isEmpty(), "Expected a candidate for " + key + ", got " + solution.status());
        return solution.candidates().get(0).get(key).valueInSi();
    }

    private static Map<ProjectileProblem.Input, PhysicalQuantity> plus(
            Map<ProjectileProblem.Input, PhysicalQuantity> source,
            ProjectileProblem.Input key, PhysicalQuantity quantity) {
        EnumMap<ProjectileProblem.Input, PhysicalQuantity> copy = new EnumMap<>(source);
        copy.put(key, quantity);
        return copy;
    }

    private static ProjectileProblem problem(ProjectileProblem.Target target,
                                             Map<ProjectileProblem.Input, PhysicalQuantity> givens) {
        return new ProjectileProblem("test problem", target, new EnumMap<>(givens));
    }

    private static PhysicalQuantity q(double value, PhysicalUnit unit) {
        return new PhysicalQuantity(value, unit);
    }
}
