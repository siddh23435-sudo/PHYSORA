package edu.physics.problem;

import edu.physics.problem.ProjectileProblemSequence.ResultReference;
import edu.physics.projectile.LandingSurface;
import edu.physics.projectile.ProjectileMotion;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static edu.physics.problem.CanonicalPhysicsProblem.AngleReference;
import static edu.physics.problem.CanonicalPhysicsProblem.SurfaceTarget;
import static edu.physics.problem.CanonicalSolution.ContactClassification;
import static edu.physics.problem.CanonicalSolution.OverallStatus;
import static edu.physics.problem.CanonicalSolution.PartStatus;
import static edu.physics.problem.ProjectileProblem.Input;
import static edu.physics.problem.ProjectileProblem.Target;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalPhysicsProblemSolverTest {
    private static final double TOL = 1e-8;
    private final ProjectileProblemSolver solver = new ProjectileProblemSolver();

    @Test
    void solvesNormalHorizontalAndInclinedReferenceMultiTargetProblems() {
        // Ordinary flat 45-degree launch
        CanonicalPhysicsProblem flat = CanonicalPhysicsProblem.builder("Ordinary flat 45-degree projectile")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(Target.HORIZONTAL_RANGE)
                .addTarget(Target.MAXIMUM_HEIGHT)
                .build();

        CanonicalSolution flatSolution = solver.solveCanonical(flat);
        assertEquals(OverallStatus.SOLVED, flatSolution.status());
        assertEquals(2.0 * 20.0 * Math.sin(Math.PI / 4.0) / 9.8,
                valueOf(flatSolution, CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), "flightTime"), TOL);
        assertEquals(400.0 / 9.8,
                valueOf(flatSolution, CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), "range"), TOL);
        assertEquals(400.0 / (4.0 * 9.8),
                valueOf(flatSolution, CanonicalPhysicsProblem.target(Target.MAXIMUM_HEIGHT), "maximumHeight"), TOL);

        // Analytical inclined-plane reference: u=25, theta=60 deg horizontal, beta=30 deg, g=10
        CanonicalPhysicsProblem inclined = CanonicalPhysicsProblem.builder("Inclined plane reference")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE), AngleReference.HORIZONTAL)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(SurfaceTarget.MAXIMUM_HEIGHT_IN_FLIGHT)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .addTarget(SurfaceTarget.LANDING_POSITION)
                .build();

        CanonicalSolution inclinedSol = solver.solveCanonical(inclined);
        assertEquals(OverallStatus.SOLVED, inclinedSol.status());
        assertEquals(5.0 / Math.sqrt(3.0),
                valueOf(inclinedSol, CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), "flightTime"), TOL);
        assertEquals(125.0 / 3.0,
                valueOf(inclinedSol, CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE), "rangeAlongSurface"), TOL);
        assertEquals(23.4375,
                valueOf(inclinedSol, CanonicalPhysicsProblem.target(SurfaceTarget.MAXIMUM_HEIGHT_IN_FLIGHT), "maximumHeightInFlight"), TOL);
        assertEquals(156.25 / (10.0 * Math.sqrt(3.0)),
                valueOf(inclinedSol, CanonicalPhysicsProblem.target(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE), "maxPerpendicularDistance"), TOL);

        CanonicalSolution.PartResult landingPart = inclinedSol.resultFor(
                CanonicalPhysicsProblem.target(SurfaceTarget.LANDING_POSITION)).orElseThrow();
        assertEquals(ContactClassification.FINITE_FLIGHT, landingPart.contactClassification());
        double xLand = landingPart.candidates().get(0).get("xLanding").valueInSi();
        double yLand = landingPart.candidates().get(0).get("yLanding").valueInSi();
        assertEquals(62.5 / Math.sqrt(3.0), xLand, TOL);
        assertEquals(62.5 / 3.0, yLand, TOL);
        assertEquals(inclined.landingSurface().heightAt(xLand), yLand, TOL);
    }

    @Test
    void unifiesPolarCartesianVelocityAndNormalizesInclineRelativeLaunchAngle() {
        double vx = 25.0 * Math.cos(Math.toRadians(60.0));
        double vy = 25.0 * Math.sin(Math.toRadians(60.0));

        CanonicalPhysicsProblem polarHorizontal = CanonicalPhysicsProblem.builder("Horizontal-referenced angle")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE), AngleReference.HORIZONTAL)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        CanonicalPhysicsProblem polarInclineRelative = CanonicalPhysicsProblem.builder("Incline-referenced angle (30 deg above 30 deg slope)")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(30.0, PhysicalUnit.DEGREE), AngleReference.INCLINE_SURFACE)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        CanonicalPhysicsProblem cartesian = CanonicalPhysicsProblem.builder("Cartesian velocity vector")
                .velocityComponents(q(vx, PhysicalUnit.METRE_PER_SECOND), q(vy, PhysicalUnit.METRE_PER_SECOND))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .build();

        assertEquals(25.0, cartesian.derivedInitialSpeed().orElseThrow(), TOL);
        assertEquals(60.0, cartesian.derivedLaunchAngleDegrees().orElseThrow(), TOL);
        assertEquals(vx, polarInclineRelative.initialVx().orElseThrow(), TOL);
        assertEquals(vy, polarInclineRelative.initialVy().orElseThrow(), TOL);
        assertEquals(AngleReference.INCLINE_SURFACE, polarInclineRelative.velocityMetadata().angleReference());

        double expectedRange = 125.0 / 3.0;
        assertEquals(expectedRange,
                valueOf(solver.solveCanonical(polarHorizontal), CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE), "rangeAlongSurface"), TOL);
        assertEquals(expectedRange,
                valueOf(solver.solveCanonical(polarInclineRelative), CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE), "rangeAlongSurface"), TOL);
        assertEquals(expectedRange,
                valueOf(solver.solveCanonical(cartesian), CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE), "rangeAlongSurface"), TOL);
    }

    @Test
    void preservesValidTargetsUnderPartialSuccessAndSeparatesNoSolutionFromUnbounded() {
        // Zero gravity diverging above 25 deg incline:
        // - position at t=2s is SOLVED
        // - flight time has NO_SOLUTION (never lands)
        // - max perpendicular clearance is UNBOUNDED (g = 0, v_perp0 > 0)
        CanonicalPhysicsProblem mixed = CanonicalPhysicsProblem.builder("Diverging zero-gravity multi-target")
                .polarVelocity(q(12.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(0.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .initialPosition(q(0.0, PhysicalUnit.METRE), q(4.0, PhysicalUnit.METRE))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 25.0))
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME),
                        Map.of(Input.TIME, q(2.0, PhysicalUnit.SECOND)))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .build();

        CanonicalSolution partial = solver.solveCanonical(mixed);
        assertEquals(OverallStatus.PARTIALLY_SOLVED, partial.status());

        CanonicalSolution.PartResult posResult = partial.resultFor(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME)).orElseThrow();
        CanonicalSolution.PartResult flightResult = partial.resultFor(CanonicalPhysicsProblem.target(Target.FLIGHT_TIME)).orElseThrow();
        CanonicalSolution.PartResult clearanceResult = partial.resultFor(CanonicalPhysicsProblem.target(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)).orElseThrow();

        assertEquals(PartStatus.SOLVED, posResult.status());
        assertEquals(24.0 * Math.cos(Math.PI / 4.0), posResult.candidates().get(0).get("x").valueInSi(), TOL);
        assertEquals(4.0 + 24.0 * Math.sin(Math.PI / 4.0), posResult.candidates().get(0).get("y").valueInSi(), TOL);

        assertEquals(PartStatus.NO_SOLUTION, flightResult.status());
        assertEquals(ContactClassification.NO_LANDING, flightResult.contactClassification());

        assertEquals(PartStatus.UNBOUNDED, clearanceResult.status());

        // When ZERO targets solve and non-solved targets disagree (NO_SOLUTION vs UNBOUNDED):
        CanonicalPhysicsProblem noSolvedMixed = CanonicalPhysicsProblem.builder("Unbounded vs no landing")
                .polarVelocity(q(12.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(0.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .initialPosition(q(0.0, PhysicalUnit.METRE), q(4.0, PhysicalUnit.METRE))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 25.0))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .build();
        CanonicalSolution split = solver.solveCanonical(noSolvedMixed);
        assertEquals(OverallStatus.NO_UNIFIED_SOLUTION, split.status());
        assertEquals(PartStatus.NO_SOLUTION, split.resultFor(CanonicalPhysicsProblem.target(Target.FLIGHT_TIME)).orElseThrow().status());
        assertEquals(PartStatus.UNBOUNDED, split.resultFor(CanonicalPhysicsProblem.target(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)).orElseThrow().status());
    }

    @Test
    void handlesAllStandardAndSurfaceTargetsAndInverseCanonicalProblems() {
        // Test coordinate along anchor, signed surface displacement, negative vx0, and trajectory point targets
        CanonicalPhysicsProblem backward = CanonicalPhysicsProblem.builder("Backward incline projection")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(135.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .initialPosition(q(2.0, PhysicalUnit.METRE), q(2.0 * Math.tan(Math.toRadians(20.0)), PhysicalUnit.METRE))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 20.0))
                .addTarget(SurfaceTarget.SIGNED_DISPLACEMENT_ALONG_SURFACE)
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(SurfaceTarget.COORDINATE_ALONG_SURFACE_FROM_ANCHOR)
                .addTarget(Target.TIME_TO_APEX)
                .addTarget(Target.SPEED_AT_APEX)
                .addTarget(CanonicalPhysicsProblem.target(Target.VELOCITY_AT_TIME), Map.of(Input.TIME, q(0.5, PhysicalUnit.SECOND)))
                .addTarget(CanonicalPhysicsProblem.target(Target.SPEED_AT_TIME), Map.of(Input.TIME, q(0.5, PhysicalUnit.SECOND)))
                .addTarget(CanonicalPhysicsProblem.target(Target.DISTANCE_TRAVELLED_AT_TIME), Map.of(Input.TIME, q(0.5, PhysicalUnit.SECOND)))
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_HORIZONTAL_DISPLACEMENT), Map.of(Input.HORIZONTAL_DISPLACEMENT, q(-5.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.TIMES_AT_HEIGHT), Map.of(Input.TARGET_Y, q(3.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.TIMES_AT_POSITION), Map.of(Input.TARGET_X, q(2.0, PhysicalUnit.METRE), Input.TARGET_Y, q(2.0 * Math.tan(Math.toRadians(20.0)), PhysicalUnit.METRE)))
                .build();

        CanonicalSolution sol = solver.solveCanonical(backward);
        assertEquals(OverallStatus.MULTIPLE_SOLUTIONS, sol.status());
        assertEquals(PartStatus.MULTIPLE_SOLUTIONS,
                sol.resultFor(CanonicalPhysicsProblem.target(Target.TIMES_AT_HEIGHT)).orElseThrow().status());
        assertEquals(2,
                sol.resultFor(CanonicalPhysicsProblem.target(Target.TIMES_AT_HEIGHT)).orElseThrow().candidates().size());
        assertEquals(PartStatus.SOLVED,
                sol.resultFor(CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE)).orElseThrow().status());
        ProjectileMotion motion = backward.toMotion();

        double signedDisp = valueOf(sol, CanonicalPhysicsProblem.target(SurfaceTarget.SIGNED_DISPLACEMENT_ALONG_SURFACE), "signedSurfaceDisplacement");
        double absRange = valueOf(sol, CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE), "rangeAlongSurface");
        assertTrue(signedDisp < 0.0);
        assertEquals(Math.abs(signedDisp), absRange, TOL);
        assertEquals(motion.signedCoordinateAlongSurfaceFromAnchor().orElseThrow(),
                valueOf(sol, CanonicalPhysicsProblem.target(SurfaceTarget.COORDINATE_ALONG_SURFACE_FROM_ANCHOR), "anchorSurfaceCoordinate"), TOL);
        assertEquals(motion.timeToApex().orElseThrow(),
                valueOf(sol, CanonicalPhysicsProblem.target(Target.TIME_TO_APEX), "timeToApex"), TOL);
        assertEquals(motion.apexState().orElseThrow().speed(),
                valueOf(sol, CanonicalPhysicsProblem.target(Target.SPEED_AT_APEX), "speedAtApex"), TOL);

        ProjectileMotion.State atHalfSecond = motion.stateAt(0.5);
        CanonicalSolution.PartResult velAtTime = sol.resultFor(CanonicalPhysicsProblem.target(Target.VELOCITY_AT_TIME)).orElseThrow();
        assertEquals(PartStatus.SOLVED, velAtTime.status());
        assertEquals(atHalfSecond.vx(), velAtTime.candidates().get(0).get("vx").valueInSi(), TOL);
        assertEquals(atHalfSecond.vy(), velAtTime.candidates().get(0).get("vy").valueInSi(), TOL);
        assertEquals(atHalfSecond.speed(),
                valueOf(sol, CanonicalPhysicsProblem.target(Target.SPEED_AT_TIME), "speed"), TOL);
        assertEquals(motion.distanceTravelledAt(0.5),
                valueOf(sol, CanonicalPhysicsProblem.target(Target.DISTANCE_TRAVELLED_AT_TIME), "distanceTravelled"), TOL);

        ProjectileMotion.State atDxMinusFive = motion.statesAtHorizontalPosition(2.0 - 5.0).get(0);
        CanonicalSolution.PartResult posAtDx = sol.resultFor(
                CanonicalPhysicsProblem.target(Target.POSITION_AT_HORIZONTAL_DISPLACEMENT)).orElseThrow();
        assertEquals(PartStatus.SOLVED, posAtDx.status());
        assertEquals(atDxMinusFive.x(), posAtDx.candidates().get(0).get("x").valueInSi(), TOL);
        assertEquals(atDxMinusFive.y(), posAtDx.candidates().get(0).get("y").valueInSi(), TOL);

        assertEquals(0.0,
                valueOf(sol, CanonicalPhysicsProblem.target(Target.TIMES_AT_POSITION), "time"), TOL);

        // Inverse targets via canonical spec
        CanonicalPhysicsProblem inverseSpeed = CanonicalPhysicsProblem.builder("Find speed from displacement")
                .polarVelocity(null, q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(0.0, PhysicalUnit.METRE)))
                .build();
        CanonicalSolution inverseSol = solver.solveCanonical(inverseSpeed);
        assertEquals(OverallStatus.SOLVED, inverseSol.status());
        assertEquals(Math.sqrt(98.0),
                valueOf(inverseSol, CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), "initialSpeed"), TOL);

        CanonicalPhysicsProblem inverseAngle = CanonicalPhysicsProblem.builder("Find angle for symmetric range")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_LAUNCH_ANGLE), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(20.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(0.0, PhysicalUnit.METRE)))
                .build();
        CanonicalSolution angleSol = solver.solveCanonical(inverseAngle);
        assertEquals(OverallStatus.MULTIPLE_SOLUTIONS, angleSol.status());
        assertEquals(2, angleSol.partResults().get(0).candidates().size());
    }

    @Test
    void detectsImmediateContactUnderdeterminedConflictingInvalidAndUnsupportedInputs() {
        // Immediate contact (T = 0) on incline
        CanonicalPhysicsProblem tangentImmediate = CanonicalPhysicsProblem.builder("Tangential surface launch")
                .polarVelocity(q(15.0, PhysicalUnit.METRE_PER_SECOND), q(30.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .build();
        CanonicalSolution immediateSol = solver.solveCanonical(tangentImmediate);
        assertEquals(OverallStatus.SOLVED, immediateSol.status());
        assertEquals(ContactClassification.IMMEDIATE_CONTACT,
                immediateSol.resultFor(CanonicalPhysicsProblem.target(Target.FLIGHT_TIME)).orElseThrow().contactClassification());
        assertEquals(0.0, valueOf(immediateSol, CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), "flightTime"), TOL);
        assertEquals(0.0, valueOf(immediateSol, CanonicalPhysicsProblem.target(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE), "maxPerpendicularDistance"), TOL);

        // Per-part UNDERDETERMINED alongside SOLVED sibling
        CanonicalPhysicsProblem missingPartParam = CanonicalPhysicsProblem.builder("Missing time for second part")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.HORIZONTAL_RANGE)
                .addTarget(Target.POSITION_AT_TIME)
                .build();
        CanonicalSolution partialMissing = solver.solveCanonical(missingPartParam);
        assertEquals(OverallStatus.PARTIALLY_SOLVED, partialMissing.status());
        assertEquals(PartStatus.SOLVED, partialMissing.resultFor(CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE)).orElseThrow().status());
        CanonicalSolution.PartResult missingPos = partialMissing.resultFor(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME)).orElseThrow();
        assertEquals(PartStatus.UNDERDETERMINED, missingPos.status());
        assertTrue(missingPos.missingInputs().contains("TIME"));

        // Conflicting velocity vector vs polar speed+angle
        CanonicalPhysicsProblem conflicting = CanonicalPhysicsProblem.builder("Conflicting velocity inputs")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .velocityComponents(q(5.0, PhysicalUnit.METRE_PER_SECOND), q(5.0, PhysicalUnit.METRE_PER_SECOND))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        CanonicalSolution conflictSol = solver.solveCanonical(conflicting);
        assertEquals(OverallStatus.INVALID_INPUT, conflictSol.status());
        assertTrue(conflictSol.diagnostics().contains("conflicting-velocity-specification"));

        // Unsupported constraint (air resistance) never numerically solved
        CanonicalPhysicsProblem unsupported = CanonicalPhysicsProblem.builder("Projectile with quadratic drag")
                .polarVelocity(q(25.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addUnsupportedConstraint("air-resistance")
                .addTarget(Target.HORIZONTAL_RANGE)
                .build();
        CanonicalSolution unsupportedSol = solver.solveCanonical(unsupported);
        assertEquals(OverallStatus.UNSUPPORTED, unsupportedSol.status());
        assertEquals(PartStatus.UNSUPPORTED, unsupportedSol.partResults().get(0).status());
        assertTrue(unsupportedSol.partResults().get(0).candidates().isEmpty());

        // Launch strictly below incline
        CanonicalPhysicsProblem belowIncline = CanonicalPhysicsProblem.builder("Launch underground below incline")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .initialPosition(q(0.0, PhysicalUnit.METRE), q(-0.5, PhysicalUnit.METRE))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        CanonicalSolution belowSol = solver.solveCanonical(belowIncline);
        assertEquals(OverallStatus.INVALID_INPUT, belowSol.status());
        assertTrue(belowSol.diagnostics().contains("launch-below-inclined-surface"));

        assertEquals(OverallStatus.INVALID_INPUT, solver.solveCanonical((CanonicalPhysicsProblem) null).status());
    }

    @Test
    void verifiesSymbolicEquationsUnitsAndLegacyProjectileBridgeConsistency() {
        CanonicalPhysicsProblem inclined = CanonicalPhysicsProblem.builder("Symbolic derivation check")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(50.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 45.0))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .addTarget(SurfaceTarget.MAXIMUM_HEIGHT_IN_FLIGHT)
                .build();

        CanonicalSolution sol = solver.solveCanonical(inclined);
        assertEquals(OverallStatus.SOLVED, sol.status());

        CanonicalSolution.PartResult rangeStep = sol.resultFor(CanonicalPhysicsProblem.target(SurfaceTarget.RANGE_ALONG_SURFACE)).orElseThrow();
        assertEquals("range-along-surface", rangeStep.steps().get(0).equationId());
        assertEquals(PhysicalUnit.DEGREE, rangeStep.steps().get(0).substitutions().get("beta").unit());
        assertEquals(PhysicalUnit.METRE, rangeStep.steps().get(0).results().get("rangeAlongSurface").unit());

        CanonicalSolution.PartResult perpStep = sol.resultFor(CanonicalPhysicsProblem.target(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)).orElseThrow();
        assertEquals("max-perpendicular-distance", perpStep.steps().get(0).equationId());
        assertEquals(PhysicalUnit.METRE_PER_SECOND_SQUARED,
                perpStep.steps().get(0).substitutions().get("g").unit());

        CanonicalSolution.PartResult maxHStep = sol.resultFor(CanonicalPhysicsProblem.target(SurfaceTarget.MAXIMUM_HEIGHT_IN_FLIGHT)).orElseThrow();
        assertEquals("maximum-height-in-flight", maxHStep.steps().get(0).equationId());
        assertTrue(maxHStep.diagnostics().contains("impact-precedes-unconstrained-vertical-apex"));
        assertEquals(inclined.toMotion().maximumHeightInFlight().orElseThrow(),
                maxHStep.candidates().get(0).get("maximumHeightInFlight").valueInSi(), TOL);

        // Legacy bridge: ProjectileProblem -> CanonicalPhysicsProblem matches solver.solve(legacy)
        ProjectileProblem legacy = new ProjectileProblem("Flat 36 km/h launch", Target.HORIZONTAL_RANGE, Map.of(
                Input.INITIAL_SPEED, q(36.0, PhysicalUnit.KILOMETRE_PER_HOUR),
                Input.LAUNCH_ANGLE, q(45.0, PhysicalUnit.DEGREE),
                Input.GRAVITY, q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED)));
        ProjectileSolution legacyResult = solver.solve(legacy);
        CanonicalSolution canonicalResult = solver.solveCanonical(CanonicalPhysicsProblem.fromProjectileProblem(legacy));
        assertEquals(legacyResult.candidates().get(0).get("range").valueInSi(),
                valueOf(canonicalResult, CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), "range"), TOL);
    }

    @Test
    void detectsAsymmetricPartialVelocityAndSurfaceConflictsAndMatchesConsistentRedundantInputs() {
        // Conflicting: complete polar + contradicting partial vx
        CanonicalPhysicsProblem polarPlusBadVx = CanonicalPhysicsProblem.builder("Polar plus contradicting vx")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .velocityComponents(q(-500.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        CanonicalSolution badVxSol = solver.solveCanonical(polarPlusBadVx);
        assertEquals(OverallStatus.INVALID_INPUT, badVxSol.status());
        assertTrue(badVxSol.diagnostics().contains("conflicting-velocity-specification"));

        // Conflicting: complete polar + contradicting partial vy
        CanonicalPhysicsProblem polarPlusBadVy = CanonicalPhysicsProblem.builder("Polar plus contradicting vy")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(30.0, PhysicalUnit.DEGREE))
                .velocityComponents(null, q(15.0, PhysicalUnit.METRE_PER_SECOND))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        assertEquals(OverallStatus.INVALID_INPUT, solver.solveCanonical(polarPlusBadVy).status());

        // Conflicting: complete Cartesian + contradicting partial speed or angle
        CanonicalPhysicsProblem cartPlusBadSpeed = CanonicalPhysicsProblem.builder("Cartesian plus bad speed")
                .velocityComponents(q(12.0, PhysicalUnit.METRE_PER_SECOND), q(16.0, PhysicalUnit.METRE_PER_SECOND))
                .polarVelocity(q(999.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        assertEquals(OverallStatus.INVALID_INPUT, solver.solveCanonical(cartPlusBadSpeed).status());

        CanonicalPhysicsProblem cartPlusBadAngle = CanonicalPhysicsProblem.builder("Cartesian plus bad angle")
                .velocityComponents(q(12.0, PhysicalUnit.METRE_PER_SECOND), q(16.0, PhysicalUnit.METRE_PER_SECOND))
                .polarVelocity(null, q(0.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        assertEquals(OverallStatus.INVALID_INPUT, solver.solveCanonical(cartPlusBadAngle).status());

        // Conflicting incomplete pair exceeding speed magnitude: speed=0, vx=10
        CanonicalPhysicsProblem zeroSpeedBigVx = CanonicalPhysicsProblem.builder("Zero speed with nonzero vx")
                .polarVelocity(q(0.0, PhysicalUnit.METRE_PER_SECOND), null)
                .velocityComponents(q(10.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        CanonicalSolution zeroBigSol = solver.solveCanonical(zeroSpeedBigVx);
        assertEquals(OverallStatus.INVALID_INPUT, zeroBigSol.status());
        assertTrue(zeroBigSol.diagnostics().contains("conflicting-velocity-specification"));

        // Consistent partial redundant inputs must succeed (including zero vector with arbitrary polar angle)
        CanonicalPhysicsProblem matchingPartialVx = CanonicalPhysicsProblem.builder("Consistent polar + vx")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .velocityComponents(q(10.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.HORIZONTAL_RANGE)
                .build();
        assertEquals(OverallStatus.SOLVED, solver.solveCanonical(matchingPartialVx).status());

        CanonicalPhysicsProblem matchingPartialSpeed = CanonicalPhysicsProblem.builder("Consistent Cartesian + speed")
                .velocityComponents(q(12.0, PhysicalUnit.METRE_PER_SECOND), q(16.0, PhysicalUnit.METRE_PER_SECOND))
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        assertEquals(3.2, valueOf(solver.solveCanonical(matchingPartialSpeed),
                CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), "flightTime"), TOL);

        CanonicalPhysicsProblem zeroVectorWithAngle = CanonicalPhysicsProblem.builder("Zero velocity does not invent angle conflict")
                .velocityComponents(q(0.0, PhysicalUnit.METRE_PER_SECOND), q(0.0, PhysicalUnit.METRE_PER_SECOND))
                .polarVelocity(null, q(37.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .initialPosition(q(0.0, PhysicalUnit.METRE), q(5.0, PhysicalUnit.METRE))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        assertEquals(1.0, valueOf(solver.solveCanonical(zeroVectorWithAngle),
                CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), "flightTime"), TOL);

        // Conflicting landingSurface and horizontalLandingY
        CanonicalPhysicsProblem surfaceConflict = CanonicalPhysicsProblem.builder("Conflicting surface definitions")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.81, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 15.0))
                .horizontalLandingY(q(2.0, PhysicalUnit.METRE))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        CanonicalSolution surfaceConflictSol = solver.solveCanonical(surfaceConflict);
        assertEquals(OverallStatus.INVALID_INPUT, surfaceConflictSol.status());
        assertTrue(surfaceConflictSol.diagnostics().contains("conflicting-landing-surface-specification"));
    }

    @Test
    void coversRemainingStandardTargetsSignedNegativeClearanceParallelZeroGAndInverseSurfaceHandling() {
        // Remaining standard targets: POSITION_AT_TIME (g>0), ...AT_HORIZONTAL_DISPLACEMENT, ...AT_HEIGHT
        CanonicalPhysicsProblem querySuite = CanonicalPhysicsProblem.builder("Standard displacement and height queries")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(60.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME),
                        Map.of(Input.TIME, q(1.0, PhysicalUnit.SECOND)))
                .addTarget(CanonicalPhysicsProblem.target(Target.VELOCITY_AT_HORIZONTAL_DISPLACEMENT),
                        Map.of(Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.SPEED_AT_HORIZONTAL_DISPLACEMENT),
                        Map.of(Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.TIME_AT_HORIZONTAL_DISPLACEMENT),
                        Map.of(Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_HEIGHT),
                        Map.of(Input.TARGET_Y, q(10.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.VELOCITY_AT_HEIGHT),
                        Map.of(Input.TARGET_Y, q(10.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.SPEED_AT_HEIGHT),
                        Map.of(Input.TARGET_Y, q(10.0, PhysicalUnit.METRE)))
                .build();

        CanonicalSolution querySol = solver.solveCanonical(querySuite);
        assertEquals(OverallStatus.MULTIPLE_SOLUTIONS, querySol.status());
        ProjectileMotion m = querySuite.toMotion();

        CanonicalSolution.PartResult posAtOne = querySol.resultFor(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME)).orElseThrow();
        assertEquals(m.stateAt(1.0).x(), posAtOne.candidates().get(0).get("x").valueInSi(), TOL);
        assertEquals(m.stateAt(1.0).y(), posAtOne.candidates().get(0).get("y").valueInSi(), TOL);

        CanonicalSolution.PartResult velAtDx = querySol.resultFor(CanonicalPhysicsProblem.target(Target.VELOCITY_AT_HORIZONTAL_DISPLACEMENT)).orElseThrow();
        assertEquals(10.0, velAtDx.candidates().get(0).get("vx").valueInSi(), TOL);
        assertEquals(10.0 * Math.sqrt(3.0) - 10.0, velAtDx.candidates().get(0).get("vy").valueInSi(), TOL);
        assertEquals(m.stateAt(1.0).speed(),
                valueOf(querySol, CanonicalPhysicsProblem.target(Target.SPEED_AT_HORIZONTAL_DISPLACEMENT), "speed"), TOL);
        assertEquals(1.0,
                valueOf(querySol, CanonicalPhysicsProblem.target(Target.TIME_AT_HORIZONTAL_DISPLACEMENT), "time"), TOL);

        CanonicalSolution.PartResult posAtH = querySol.resultFor(CanonicalPhysicsProblem.target(Target.POSITION_AT_HEIGHT)).orElseThrow();
        CanonicalSolution.PartResult velAtH = querySol.resultFor(CanonicalPhysicsProblem.target(Target.VELOCITY_AT_HEIGHT)).orElseThrow();
        CanonicalSolution.PartResult spdAtH = querySol.resultFor(CanonicalPhysicsProblem.target(Target.SPEED_AT_HEIGHT)).orElseThrow();
        assertEquals(2, posAtH.candidates().size());
        assertEquals(2, velAtH.candidates().size());
        assertEquals(2, spdAtH.candidates().size());
        assertEquals(Math.sqrt(200.0), spdAtH.candidates().get(0).get("speed").valueInSi(), TOL);

        // Negative bounded MAX_SIGNED_PERPENDICULAR_DISTANCE (-5 m) vs MAX_PERPENDICULAR_DISTANCE (NO_SOLUTION)
        CanonicalPhysicsProblem belowCeiling = CanonicalPhysicsProblem.builder("Unreachable plateau at y = 10 m")
                .polarVelocity(q(10.0, PhysicalUnit.METRE_PER_SECOND), q(90.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(10.0, PhysicalUnit.METRE))
                .addTarget(SurfaceTarget.MAX_SIGNED_PERPENDICULAR_DISTANCE)
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .build();
        CanonicalSolution belowCeilingSol = solver.solveCanonical(belowCeiling);
        assertEquals(OverallStatus.PARTIALLY_SOLVED, belowCeilingSol.status());
        assertEquals(-5.0, valueOf(belowCeilingSol,
                CanonicalPhysicsProblem.target(SurfaceTarget.MAX_SIGNED_PERPENDICULAR_DISTANCE),
                "maxSignedPerpendicularDistance"), TOL);
        assertEquals(PartStatus.NO_SOLUTION, belowCeilingSol.resultFor(
                CanonicalPhysicsProblem.target(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)).orElseThrow().status());

        // Parallel zero-gravity flight exercises surface-perpendicular-clearance symbolic equation
        CanonicalPhysicsProblem parallelZeroG = CanonicalPhysicsProblem.builder("Parallel zero-gravity incline flight")
                .polarVelocity(q(12.0, PhysicalUnit.METRE_PER_SECOND), q(25.0, PhysicalUnit.DEGREE))
                .gravity(q(0.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .initialPosition(q(0.0, PhysicalUnit.METRE), q(4.0, PhysicalUnit.METRE))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 25.0))
                .addTarget(SurfaceTarget.MAX_PERPENDICULAR_DISTANCE)
                .build();
        CanonicalSolution parallelSol = solver.solveCanonical(parallelZeroG);
        assertEquals(OverallStatus.SOLVED, parallelSol.status());
        CanonicalSolution.PartResult parallelPart = parallelSol.partResults().get(0);
        assertEquals("surface-perpendicular-clearance", parallelPart.steps().get(0).equationId());
        assertEquals(4.0 * Math.cos(Math.toRadians(25.0)),
                parallelPart.candidates().get(0).get("maxPerpendicularDistance").valueInSi(), TOL);

        // Inverse targets with InclinedPlane: pure (dx, dy) succeeds & preserves incline assumptions;
        // dx-only without dy is rejected cleanly as UNSUPPORTED
        CanonicalPhysicsProblem supportedInverseOnIncline = CanonicalPhysicsProblem.builder("Supported inverse over incline")
                .polarVelocity(null, q(15.0, PhysicalUnit.DEGREE), AngleReference.INCLINE_SURFACE)
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(0.0, PhysicalUnit.METRE)))
                .build();
        CanonicalSolution invSupportedSol = solver.solveCanonical(supportedInverseOnIncline);
        assertEquals(OverallStatus.SOLVED, invSupportedSol.status());
        assertEquals(Math.sqrt(98.0),
                valueOf(invSupportedSol, CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), "initialSpeed"), TOL);
        assertTrue(invSupportedSol.partResults().get(0).assumptions().contains("landing-surface-inclined"));
        assertFalse(invSupportedSol.partResults().get(0).assumptions().contains("landing-y-defaulted-to-initial-y"));

        CanonicalPhysicsProblem unsupportedSurfaceInverse = CanonicalPhysicsProblem.builder("Inverse missing vertical offset on incline")
                .polarVelocity(null, q(15.0, PhysicalUnit.DEGREE), AngleReference.INCLINE_SURFACE)
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED),
                        Map.of(Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE)))
                .build();
        CanonicalSolution invUnsupportedSol = solver.solveCanonical(unsupportedSurfaceInverse);
        assertEquals(OverallStatus.UNSUPPORTED, invUnsupportedSol.status());
        assertEquals(PartStatus.UNSUPPORTED, invUnsupportedSol.partResults().get(0).status());

        // Policy rule: solvable part + INVALID_INPUT part => overall INVALID_INPUT (not PARTIALLY_SOLVED),
        // while preserving valid part's result and invalid part's diagnostics; repeatable build()
        CanonicalPhysicsProblem.Builder mixedInvalidBuilder = CanonicalPhysicsProblem.builder("Mixed valid and invalid parameter")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.HORIZONTAL_RANGE)
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME),
                        Map.of(Input.TIME, q(-1.0, PhysicalUnit.SECOND)));
        CanonicalPhysicsProblem buildOne = mixedInvalidBuilder.build();
        CanonicalPhysicsProblem buildTwo = mixedInvalidBuilder.build();
        assertEquals(buildOne.assumptions(), buildTwo.assumptions());
        assertEquals(buildOne.validationErrors(), buildTwo.validationErrors());

        CanonicalSolution mixedInvalidSol = solver.solveCanonical(buildOne);
        assertEquals(OverallStatus.INVALID_INPUT, mixedInvalidSol.status());
        assertEquals(PartStatus.SOLVED, mixedInvalidSol.resultFor(CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE)).orElseThrow().status());
        assertEquals(40.0, valueOf(mixedInvalidSol, CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), "range"), TOL);
        assertEquals(PartStatus.INVALID_INPUT, mixedInvalidSol.resultFor(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME)).orElseThrow().status());
    }

    @Test
    void solvesDependentMultiPartProblemWithInitialSpeedReference() {
        // Part 0: FIND_INITIAL_SPEED with angle=45°, dx=10.0 m, dy=0.0 m, g=9.8
        // Part 1: HORIZONTAL_RANGE with INITIAL_SPEED referencing Part 0 "initialSpeed"
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Solve initial speed then find range")
                .polarVelocity(null, q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(0.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), Map.of(), Map.of(
                        Input.INITIAL_SPEED, new ResultReference(0, "initialSpeed")))
                .build();

        CanonicalSolution solution = solver.solveCanonical(problem);
        assertEquals(OverallStatus.SOLVED, solution.status());
        assertEquals(2, solution.partResults().size());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(0).status());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(1).status());
        double speed = solution.partResults().get(0).candidates().get(0).get("initialSpeed").valueInSi();
        assertEquals(Math.sqrt(98.0), speed, TOL);
        double range = solution.partResults().get(1).candidates().get(0).get("range").valueInSi();
        assertEquals(10.0, range, TOL);
    }

    @Test
    void solvesDependentMultiPartProblemWithFlightTimeReference() {
        // Part 0: FLIGHT_TIME with v0=20 m/s, angle=45°, g=10.0
        // Part 1: POSITION_AT_TIME with TIME referencing Part 0 "flightTime"
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Flight time then position at landing")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME), Map.of(), Map.of(
                        Input.TIME, new ResultReference(0, "flightTime")))
                .build();

        CanonicalSolution solution = solver.solveCanonical(problem);
        assertEquals(OverallStatus.SOLVED, solution.status());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(0).status());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(1).status());
        double tFlight = solution.partResults().get(0).candidates().get(0).get("flightTime").valueInSi();
        assertEquals(2.0 * 20.0 * Math.sin(Math.toRadians(45.0)) / 10.0, tFlight, TOL);
        double yPos = solution.partResults().get(1).candidates().get(0).get("y").valueInSi();
        assertEquals(0.0, yPos, TOL);
    }

    @Test
    void handlesMultipleSolutionsUpstreamAsDependencyUnavailable() {
        // Part 0: FIND_LAUNCH_ANGLE that has multiple solutions
        // Part 1: FLIGHT_TIME referencing Part 0 "launchAngle"
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Multiple roots upstream")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_LAUNCH_ANGLE), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(2.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), Map.of(), Map.of(
                        Input.LAUNCH_ANGLE, new ResultReference(0, "launchAngle")))
                .build();

        CanonicalSolution solution = solver.solveCanonical(problem);
        assertEquals(OverallStatus.PARTIALLY_SOLVED, solution.status());
        assertEquals(PartStatus.MULTIPLE_SOLUTIONS, solution.partResults().get(0).status());
        assertEquals(2, solution.partResults().get(0).candidates().size());
        assertEquals(PartStatus.DEPENDENCY_UNAVAILABLE, solution.partResults().get(1).status());
        assertTrue(solution.partResults().get(1).diagnostics().contains("upstream-part-not-uniquely-solved:0"));
    }

    @Test
    void handlesUpstreamFailureAndMissingQuantityAsDependencyUnavailable() {
        // Target requiring unreachable point -> NO_SOLUTION upstream
        CanonicalPhysicsProblem unreachable = CanonicalPhysicsProblem.builder("Unreachable point upstream")
                .polarVelocity(q(5.0, PhysicalUnit.METRE_PER_SECOND), null)
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_LAUNCH_ANGLE), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(100.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.FLIGHT_TIME), Map.of(), Map.of(
                        Input.LAUNCH_ANGLE, new ResultReference(0, "launchAngle")))
                .build();
        CanonicalSolution unreachSol = solver.solveCanonical(unreachable);
        assertEquals(OverallStatus.NO_UNIFIED_SOLUTION, unreachSol.status());
        assertEquals(PartStatus.NO_SOLUTION, unreachSol.partResults().get(0).status());
        assertEquals(PartStatus.DEPENDENCY_UNAVAILABLE, unreachSol.partResults().get(1).status());

        // Referencing non-existent quantity
        CanonicalPhysicsProblem badKey = CanonicalPhysicsProblem.builder("Missing quantity name")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME), Map.of(), Map.of(
                        Input.TIME, new ResultReference(0, "nonExistentKey")))
                .build();
        CanonicalSolution badKeySol = solver.solveCanonical(badKey);
        assertEquals(OverallStatus.PARTIALLY_SOLVED, badKeySol.status());
        assertEquals(PartStatus.SOLVED, badKeySol.partResults().get(0).status());
        assertEquals(PartStatus.DEPENDENCY_UNAVAILABLE, badKeySol.partResults().get(1).status());
        assertTrue(badKeySol.partResults().get(1).diagnostics().contains("referenced-result-not-found:nonExistentKey"));
    }

    @Test
    void rejectsSelfForwardAndConflictingReferencesAtBuildAndEvaluation() {
        // 1. Negative reference index: rejected at reference construction with IllegalArgumentException
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ResultReference(-1, "time"));
        assertTrue(ex.getMessage().contains("Referenced part index must be non-negative"));

        // 2. Self reference: Part 0 references Part 0
        CanonicalPhysicsProblem selfRef = CanonicalPhysicsProblem.builder("Self reference")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME), Map.of(), Map.of(
                        Input.TIME, new ResultReference(0, "time")))
                .build();
        assertFalse(selfRef.isValidForEvaluation());
        assertTrue(selfRef.validationErrors().contains("invalid-dependency-reference:TIME"));
        CanonicalSolution selfSol = solver.solveCanonical(selfRef);
        assertEquals(OverallStatus.INVALID_INPUT, selfSol.status());
        assertEquals(PartStatus.INVALID_INPUT, selfSol.partResults().get(0).status());
        assertTrue(selfSol.partResults().get(0).diagnostics().contains("invalid-dependency-reference:TIME"));

        // 3. Forward reference: Part 0 references Part 1
        CanonicalPhysicsProblem fwdRef = CanonicalPhysicsProblem.builder("Forward reference")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME), Map.of(), Map.of(
                        Input.TIME, new ResultReference(1, "time")))
                .addTarget(Target.FLIGHT_TIME)
                .build();
        assertFalse(fwdRef.isValidForEvaluation());
        assertTrue(fwdRef.validationErrors().contains("invalid-dependency-reference:TIME"));
        CanonicalSolution fwdSol = solver.solveCanonical(fwdRef);
        assertEquals(OverallStatus.INVALID_INPUT, fwdSol.status());
        assertEquals(PartStatus.INVALID_INPUT, fwdSol.partResults().get(0).status());
        assertTrue(fwdSol.partResults().get(0).diagnostics().contains("invalid-dependency-reference:TIME"));

        // 4. Conflicting input definition: same part defines parameter TIME and reference TIME
        CanonicalPhysicsProblem conflictParam = CanonicalPhysicsProblem.builder("Conflicting param and reference")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_TIME),
                        Map.of(Input.TIME, q(2.0, PhysicalUnit.SECOND)),
                        Map.of(Input.TIME, new ResultReference(0, "flightTime")))
                .build();
        assertFalse(conflictParam.isValidForEvaluation());
        assertTrue(conflictParam.validationErrors().contains("conflicting-input-definition:TIME"));
        CanonicalSolution conflictParamSol = solver.solveCanonical(conflictParam);
        assertEquals(OverallStatus.INVALID_INPUT, conflictParamSol.status());
        assertEquals(PartStatus.INVALID_INPUT, conflictParamSol.partResults().get(1).status());
        assertTrue(conflictParamSol.partResults().get(1).diagnostics().contains("conflicting-input-definition:TIME"));

        // 5. Conflict with canonical problem level input: problem sets polar speed, part 1 references INITIAL_SPEED
        CanonicalPhysicsProblem conflictProblemLevel = CanonicalPhysicsProblem.builder("Conflict with canonical speed")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), Map.of(), Map.of(
                        Input.INITIAL_SPEED, new ResultReference(0, "flightTime")))
                .build();
        assertFalse(conflictProblemLevel.isValidForEvaluation());
        assertTrue(conflictProblemLevel.validationErrors().contains("conflicting-input-definition:INITIAL_SPEED"));
        CanonicalSolution conflictProblemSol = solver.solveCanonical(conflictProblemLevel);
        assertEquals(OverallStatus.INVALID_INPUT, conflictProblemSol.status());
        assertEquals(PartStatus.INVALID_INPUT, conflictProblemSol.partResults().get(1).status());
        assertTrue(conflictProblemSol.partResults().get(1).diagnostics().contains("conflicting-input-definition:INITIAL_SPEED"));
    }

    @Test
    void solvesIndependentSiblingPartWhenEarlierDependencyChainFails() {
        // Physical setup:
        // Canonical problem defines launch angle = 45 degrees, g = 10 m/s^2, flat landing at y = 0.
        // Part 0 (inverse): FIND_INITIAL_SPEED to hit (dx = 10 m, dy = 20 m).
        // Physics justification: Launch angle is 45 deg, so launch line is y = x.
        // Because gravity curves trajectories strictly downward, y(x) < x for all positive x and real v0.
        // A target at (10, 20) requires curving upward (y > x), which is physically impossible under gravity.
        // Therefore, Part 0 produces NO_SOLUTION.
        //
        // Part 1 (dependent): HORIZONTAL_RANGE with INITIAL_SPEED referencing Part 0 "initialSpeed".
        // Since Part 0 produces NO_SOLUTION, Part 1 cannot resolve initialSpeed -> DEPENDENCY_UNAVAILABLE.
        //
        // Part 2 (independent sibling): HORIZONTAL_RANGE with its own initialSpeed = 20 m/s in parameters,
        // using problem-level angle = 45 deg, g = 10 m/s^2.
        // Analytical result: R = v0^2 * sin(2*theta) / g = 20^2 * sin(90 deg) / 10 = 40.0 m.
        // Part 2 must genuinely evaluate and solve to R = 40.0 m.
        //
        // Overall status: Part 0 is NO_SOLUTION, Part 1 is DEPENDENCY_UNAVAILABLE, Part 2 is SOLVED.
        // With at least one part solved and not all solved, overall status must be PARTIALLY_SOLVED.
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Independent sibling evaluation")
                .polarVelocity(null, q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .horizontalLandingY(q(0.0, PhysicalUnit.METRE))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(20.0, PhysicalUnit.METRE)))
                .addTarget(CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), Map.of(), Map.of(
                        Input.INITIAL_SPEED, new ResultReference(0, "initialSpeed")))
                .addTarget(CanonicalPhysicsProblem.target(Target.HORIZONTAL_RANGE), Map.of(
                        Input.INITIAL_SPEED, q(20.0, PhysicalUnit.METRE_PER_SECOND)))
                .build();

        assertTrue(problem.isValidForEvaluation());
        assertEquals(3, problem.parts().size());

        CanonicalSolution solution = solver.solveCanonical(problem);

        assertEquals(OverallStatus.PARTIALLY_SOLVED, solution.status());
        assertEquals(3, solution.partResults().size());

        // Part 0 assertions: inverse query failed with NO_SOLUTION
        CanonicalSolution.PartResult part0 = solution.partResults().get(0);
        assertEquals(PartStatus.NO_SOLUTION, part0.status());
        assertTrue(part0.candidates().isEmpty());

        // Part 1 assertions: dependent part failed with DEPENDENCY_UNAVAILABLE
        CanonicalSolution.PartResult part1 = solution.partResults().get(1);
        assertEquals(PartStatus.DEPENDENCY_UNAVAILABLE, part1.status());
        assertTrue(part1.candidates().isEmpty());
        assertTrue(part1.diagnostics().contains("upstream-part-not-uniquely-solved:0"));

        // Part 2 assertions: independent sibling genuinely evaluated and SOLVED
        CanonicalSolution.PartResult part2 = solution.partResults().get(2);
        assertEquals(PartStatus.SOLVED, part2.status());
        assertEquals(1, part2.candidates().size());
        assertEquals(40.0, part2.candidates().get(0).get("range").valueInSi(), TOL);
        assertEquals(PhysicalUnit.METRE, part2.candidates().get(0).get("range").unit());

        // Prove Part 2 was genuinely calculated via forward kinematic step, not merely flagged
        assertFalse(part2.steps().isEmpty());
        assertEquals("range-from-landing-time", part2.steps().get(0).equationId());
        assertTrue(part2.requiredQuantities().contains("range"));
        assertTrue(part2.assumptions().contains("constant-downward-gravity"));
    }

    @Test
    void rejectsIncompatibleUnitReference() {
        // Part 0 produces flightTime (TIME dimension)
        // Part 1 references it for TARGET_Y (LENGTH dimension)
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Dimension mismatch in reference")
                .polarVelocity(q(20.0, PhysicalUnit.METRE_PER_SECOND), q(45.0, PhysicalUnit.DEGREE))
                .gravity(q(10.0, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .addTarget(Target.FLIGHT_TIME)
                .addTarget(CanonicalPhysicsProblem.target(Target.POSITION_AT_HEIGHT), Map.of(), Map.of(
                        Input.TARGET_Y, new ResultReference(0, "flightTime")))
                .build();

        CanonicalSolution solution = solver.solveCanonical(problem);
        assertEquals(OverallStatus.INVALID_INPUT, solution.status());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(0).status());
        assertEquals(PartStatus.INVALID_INPUT, solution.partResults().get(1).status());
        assertTrue(solution.partResults().get(1).diagnostics().contains("incompatible-unit:TARGET_Y"));
    }

    @Test
    void solvesDependentMultiPartSequenceOnInclinedLandingSurface() {
        // Incline with angle=30°. Launch at 45° horizontal (15° relative to incline), g=9.8
        // Part 0: FIND_INITIAL_SPEED to hit (dx=10, dy=0)
        // Part 1: RANGE_ALONG_SURFACE on the incline using resolved speed!
        CanonicalPhysicsProblem problem = CanonicalPhysicsProblem.builder("Incline dependent sequence")
                .polarVelocity(null, q(15.0, PhysicalUnit.DEGREE), AngleReference.INCLINE_SURFACE)
                .gravity(q(9.8, PhysicalUnit.METRE_PER_SECOND_SQUARED))
                .landingSurface(LandingSurface.incline(0.0, 0.0, 30.0))
                .addTarget(CanonicalPhysicsProblem.target(Target.FIND_INITIAL_SPEED), Map.of(
                        Input.HORIZONTAL_DISPLACEMENT, q(10.0, PhysicalUnit.METRE),
                        Input.VERTICAL_DISPLACEMENT, q(0.0, PhysicalUnit.METRE)))
                .addTarget(SurfaceTarget.RANGE_ALONG_SURFACE, Map.of(), Map.of(
                        Input.INITIAL_SPEED, new ResultReference(0, "initialSpeed")))
                .build();

        CanonicalSolution solution = solver.solveCanonical(problem);
        assertEquals(OverallStatus.SOLVED, solution.status());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(0).status());
        assertEquals(PartStatus.SOLVED, solution.partResults().get(1).status());
        double speed = solution.partResults().get(0).candidates().get(0).get("initialSpeed").valueInSi();
        assertEquals(Math.sqrt(98.0), speed, TOL);
        double rangeAlong = solution.partResults().get(1).candidates().get(0).get("rangeAlongSurface").valueInSi();
        assertTrue(rangeAlong > 0.0);
    }

    private static double valueOf(CanonicalSolution solution, CanonicalPhysicsProblem.CanonicalTarget target, String key) {
        CanonicalSolution.PartResult part = solution.resultFor(target).orElseThrow();
        assertTrue(part.isSolved(), () -> "Expected solved target " + target + ", got " + part.status() + " " + part.diagnostics());
        return part.candidates().get(0).get(key).valueInSi();
    }

    private static PhysicalQuantity q(double value, PhysicalUnit unit) {
        return new PhysicalQuantity(value, unit);
    }
}
