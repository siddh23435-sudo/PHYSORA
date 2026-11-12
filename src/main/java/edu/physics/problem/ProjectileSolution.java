package edu.physics.problem;

import java.util.List;
import java.util.Map;
import java.math.RoundingMode;

/** Structured solver output intended for later UI and explanation rendering. */
public record ProjectileSolution(
        Status status,
        ProjectileProblem.Target target,
        String problemStatement,
        Map<ProjectileProblem.Input, PhysicalQuantity> givens,
        List<String> requiredQuantities,
        String coordinateConvention,
        List<Map<String, PhysicalQuantity>> candidates,
        List<Step> steps,
        List<ProjectileProblem.Input> missingInputs,
        List<String> assumptions,
        List<String> warnings) {

    public enum Status { SOLVED, MULTIPLE_SOLUTIONS, NO_SOLUTION, UNDERDETERMINED, AMBIGUOUS, UNBOUNDED, INVALID_INPUT }

    public record PrecisionPolicy(int displayedDecimalPlaces, RoundingMode roundingMode) {
        public PrecisionPolicy {
            if (displayedDecimalPlaces < 0) throw new IllegalArgumentException("Displayed decimal places must be non-negative");
            if (roundingMode == null) throw new IllegalArgumentException("Rounding mode is required");
        }
        public static PrecisionPolicy standardDisplay() { return new PrecisionPolicy(2, RoundingMode.HALF_UP); }
    }

    public record Step(MathExpression.Equation equation,
                       Map<String, PhysicalQuantity> substitutions,
                       Map<String, PhysicalQuantity> intermediateResults,
                       Map<String, PhysicalQuantity> results,
                       PrecisionPolicy precision,
                       String interpretationId) {
        public Step {
            if (equation == null || precision == null) throw new IllegalArgumentException("Equation and precision policy are required");
            substitutions = Map.copyOf(substitutions);
            intermediateResults = Map.copyOf(intermediateResults);
            results = Map.copyOf(results);
            if (interpretationId == null || interpretationId.isBlank()) throw new IllegalArgumentException("Interpretation identity is required");
        }
        public String equationId() { return equation.id(); }
    }

    public ProjectileSolution {
        givens = Map.copyOf(givens);
        requiredQuantities = List.copyOf(requiredQuantities);
        candidates = candidates.stream().map(Map::copyOf).toList();
        steps = List.copyOf(steps);
        missingInputs = List.copyOf(missingInputs);
        assumptions = List.copyOf(assumptions);
        warnings = List.copyOf(warnings);
    }

    public ProjectileSolution withProblem(ProjectileProblem problem) {
        return new ProjectileSolution(status, target, problem.statement(), problem.givens(), requiredFor(target),
                "Cartesian coordinates: +x right, +y up; launch angle counterclockwise from +x; SI units; landing uses the first future downward crossing.",
                candidates, steps, missingInputs, assumptions, warnings);
    }

    private static List<String> requiredFor(ProjectileProblem.Target target) {
        if (target == null) return List.of();
        return switch (target) {
            case HORIZONTAL_RANGE -> List.of("range");
            case MAXIMUM_HEIGHT -> List.of("maximumHeight");
            case TIME_TO_APEX -> List.of("timeToApex");
            case FLIGHT_TIME -> List.of("flightTime");
            case SPEED_AT_APEX -> List.of("speedAtApex");
            case VELOCITY_AT_TIME, VELOCITY_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HEIGHT -> List.of("vx", "vy");
            case SPEED_AT_TIME, SPEED_AT_HORIZONTAL_DISPLACEMENT, SPEED_AT_HEIGHT -> List.of("speed");
            case POSITION_AT_TIME, POSITION_AT_HORIZONTAL_DISPLACEMENT, POSITION_AT_HEIGHT -> List.of("x", "y");
            case TIMES_AT_HEIGHT, TIMES_AT_POSITION, TIME_AT_HORIZONTAL_DISPLACEMENT -> List.of("time");
            case DISTANCE_TRAVELLED_AT_TIME -> List.of("distanceTravelled");
            case FIND_INITIAL_SPEED -> List.of("initialSpeed");
            case FIND_LAUNCH_ANGLE -> List.of("launchAngle");
        };
    }
}
