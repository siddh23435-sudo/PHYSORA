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
        assertNotNull(range.steps().get(0).equation().left());
        assertFalse(range.steps().get(0).equation().symbols().isEmpty());
        assertEquals("range-from-landing-time", range.steps().get(0).equation().id());
        assertTrue(range.steps().get(0).equation().render().contains("tLanding"));
        assertEquals(ProjectileSolution.PrecisionPolicy.standardDisplay(), range.steps().get(0).precision());
        assertEquals(20.0, range.givens().get(INITIAL_SPEED).value(), 0.0);
        assertTrue(range.coordinateConvention().contains("+y up"));
        assertEquals(PhysicalUnit.METRE_PER_SECOND,
                range.steps().get(0).substitutions().get("vx0").unit());
        assertEquals(PhysicalUnit.METRE,
                range.steps().get(0).results().get("range").unit());
        assertTrue(range.steps().get(0).intermediateResults().isEmpty());
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
    void validatesMissingProblemsNegativeTimeAndUnitConversionOverflow() {
        assertEquals(ProjectileSolution.Status.INVALID_INPUT, solver.solve(null).status());

        ProjectileSolution negativeTime = solver.solve(problem(POSITION_AT_TIME, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(-1, PhysicalUnit.SECOND))));
        assertEquals(ProjectileSolution.Status.INVALID_INPUT, negativeTime.status());
        assertTrue(negativeTime.warnings().contains("negative-nonnegative-quantity:TIME"));

        ProjectileSolution conversionOverflow = solver.solve(problem(HORIZONTAL_RANGE, Map.of(
                INITIAL_SPEED, q(Double.MAX_VALUE, PhysicalUnit.KILOMETRE_PER_HOUR),
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                INITIAL_X, q(Double.MAX_VALUE, PhysicalUnit.KILOMETRE))));
        assertEquals(ProjectileSolution.Status.INVALID_INPUT, conversionOverflow.status());
        assertTrue(conversionOverflow.warnings().contains("unit-conversion-overflow:INITIAL_X"));
    }

    @Test
    void landingSolutionRetainsBothHeightRootsAndSelectsTheDescendingEvent() {
        ProjectileSolution solution = solver.solve(problem(FLIGHT_TIME, Map.of(
                INITIAL_SPEED, q(20, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(90, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                INITIAL_Y, q(0, PhysicalUnit.METRE),
                LANDING_Y, q(15, PhysicalUnit.METRE))));
        ProjectileSolution.Step step = solution.steps().get(0);
        assertEquals(ProjectileSolution.Status.SOLVED, solution.status());
        assertEquals(3.0, step.results().get("flightTime").valueInSi(), TOL);
        assertEquals(1.0, step.intermediateResults().get("crossingTime1").valueInSi(), TOL);
        assertEquals(3.0, step.intermediateResults().get("crossingTime2").valueInSi(), TOL);
        assertEquals(PhysicalUnit.SECOND, step.intermediateResults().get("crossingTime1").unit());
        assertTrue(solution.coordinateConvention().contains("downward crossing"));
    }

    @Test
    void inverseSpeedAndAngleHandleUniqueMultipleAndImpossibleConditions() {
        ProjectileProblem speedProblem = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(45, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(10, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(0, PhysicalUnit.METRE)));
        ProjectileSolution inverseSpeed = solver.solve(speedProblem);
        assertEquals(Math.sqrt(98), inverseSpeed.candidates().get(0).get("initialSpeed").valueInSi(), TOL);
        assertEquals(10.0, inverseSpeed.steps().get(0).intermediateResults().get("requiredHeight").valueInSi(), TOL);
        assertEquals(PhysicalUnit.METRE, inverseSpeed.steps().get(0).intermediateResults().get("requiredHeight").unit());

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
        assertTrue(angles.steps().get(0).intermediateResults().containsKey("tangentCandidate1"));
        assertEquals(PhysicalUnit.ONE, angles.steps().get(0).intermediateResults().get("tangentCandidate2").unit());

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
        ProjectileSolution solution = solver.solve(problem);
        assertEquals(20, solution.candidates().get(0).get("initialSpeed").valueInSi(), TOL);
        assertEquals(10, solution.steps().get(0).intermediateResults()
                .get("gravityVelocityContribution").valueInSi(), TOL);
        assertEquals(10, solution.steps().get(0).intermediateResults()
                .get("launchVerticalComponent").valueInSi(), TOL);
    }

    @Test
    void inverseInitialSpeedSupportsHorizontalVelocityAndSpeedConditions() {
        ProjectileProblem horizontalComponent = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(0, PhysicalUnit.DEGREE),
                GRAVITY, q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TARGET_VX, q(12, PhysicalUnit.METRE_PER_SECOND)));
        ProjectileSolution horizontal = solver.solve(horizontalComponent);
        assertEquals(12, value(horizontal, "initialSpeed"), TOL);
        assertEquals(1.0, horizontal.steps().get(0).intermediateResults().get("cosTheta").valueInSi(), TOL);

        ProjectileProblem speedAtTime = problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(1, PhysicalUnit.SECOND),
                TARGET_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND)));
        ProjectileSolution roots = solver.solve(speedAtTime);
        assertEquals(ProjectileSolution.Status.MULTIPLE_SOLUTIONS, roots.status());
        assertEquals(0, roots.candidates().get(0).get("initialSpeed").valueInSi(), TOL);
        assertEquals(10, roots.candidates().get(1).get("initialSpeed").valueInSi(), TOL);
        assertEquals(5.0, roots.steps().get(0).intermediateResults().get("speedRoot").valueInSi(), TOL);
        assertEquals(PhysicalUnit.ONE, roots.steps().get(0).intermediateResults().get("normalizedDiscriminant").unit());

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
        assertEquals(-5.0, result.steps().get(0).intermediateResults().get("gravityVelocityChange").valueInSi(), TOL);
        assertEquals(PhysicalUnit.METRE_PER_SECOND,
                result.steps().get(0).intermediateResults().get("gravityVelocityChange").unit());
        assertEquals(5.0, result.steps().get(0).substitutions().get("vy0").valueInSi(), TOL);

        ProjectileSolution position = solver.solve(problem(POSITION_AT_TIME, Map.of(
                INITIAL_SPEED, q(10, PhysicalUnit.METRE_PER_SECOND),
                LAUNCH_ANGLE, q(30, PhysicalUnit.DEGREE),
                GRAVITY, q(10, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                INITIAL_X, q(2, PhysicalUnit.METRE), INITIAL_Y, q(3, PhysicalUnit.METRE),
                TIME, q(0.5, PhysicalUnit.SECOND))));
        ProjectileSolution.Step positionStep = position.steps().get(0);
        assertEquals(4.330127018922193,
                positionStep.intermediateResults().get("horizontalDisplacement").valueInSi(), TOL);
        assertEquals(2.5, positionStep.intermediateResults().get("verticalLinearChange").valueInSi(), TOL);
        assertEquals(1.25, positionStep.intermediateResults().get("gravityDrop").valueInSi(), TOL);
        assertEquals(6.330127018922193, positionStep.results().get("x").valueInSi(), TOL);
        assertEquals(4.25, positionStep.results().get("y").valueInSi(), TOL);
        assertEquals(PhysicalUnit.METRE, positionStep.intermediateResults().get("gravityDrop").unit());
        assertEquals(2, positionStep.precision().displayedDecimalPlaces());

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
        assertEquals(1.0, velocities.steps().get(0).intermediateResults().get("crossingTime1").valueInSi(), TOL);
        assertEquals(3.0, velocities.steps().get(0).intermediateResults().get("crossingTime2").valueInSi(), TOL);
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
        assertEquals(2, multiple.steps().get(0).intermediateResults().size());

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

    @Test
    void zeroGravityInverseCasesReturnStructuredEquationsAndZeroLandingRoots() {
        ProjectileSolution speedAtLaunch = solver.solve(problem(FIND_INITIAL_SPEED, Map.of(
                LAUNCH_ANGLE, q(35, PhysicalUnit.DEGREE),
                GRAVITY, q(0, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                TIME, q(0, PhysicalUnit.SECOND),
                TARGET_SPEED, q(12, PhysicalUnit.METRE_PER_SECOND))));
        assertEquals(ProjectileSolution.Status.SOLVED, speedAtLaunch.status());
        assertEquals("initial-speed-from-speed", speedAtLaunch.steps().get(0).equationId());

        ProjectileSolution angle = solver.solve(problem(FIND_LAUNCH_ANGLE, Map.of(
                INITIAL_SPEED, q(12, PhysicalUnit.METRE_PER_SECOND),
                GRAVITY, q(0, PhysicalUnit.METRE_PER_SECOND_SQUARED),
                HORIZONTAL_DISPLACEMENT, q(4, PhysicalUnit.METRE),
                VERTICAL_DISPLACEMENT, q(3, PhysicalUnit.METRE))));
        assertEquals(ProjectileSolution.Status.SOLVED, angle.status());
        assertEquals("launch-angle-from-displacement", angle.steps().get(0).equationId());
        assertEquals(PhysicalUnit.DEGREE, angle.candidates().get(0).get("launchAngle").unit());
    }
}
