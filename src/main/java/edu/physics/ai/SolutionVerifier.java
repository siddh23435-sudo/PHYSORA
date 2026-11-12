package edu.physics.ai;

import edu.physics.problem.PhysicalQuantity;
import edu.physics.problem.PhysicalUnit;
import edu.physics.problem.ProjectileProblem;
import edu.physics.problem.ProjectileProblemSequence;
import edu.physics.problem.ProjectileSequenceSolution;
import edu.physics.problem.ProjectileSolution;
import edu.physics.projectile.ProjectileMotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks solver-result completeness, units, finiteness, and equation/result association. */
public final class SolutionVerifier {
    private static final double RELATIVE_TOLERANCE = 1e-9;

    public Verification verify(ProjectileProblemSequence problem, ProjectileSequenceSolution solution) {
        List<String> issues = new ArrayList<>();
        if (problem == null || solution == null) return new Verification(false, List.of("problem-and-solution-required"));
        boolean completeUnique = solution.status() == ProjectileSolution.Status.SOLVED;
        boolean completeWithFinalAlternatives = solution.status() == ProjectileSolution.Status.MULTIPLE_SOLUTIONS
                && solution.completedParts().size() == problem.parts().size()
                && solution.completedParts().get(solution.completedParts().size() - 1).solution().status()
                == ProjectileSolution.Status.MULTIPLE_SOLUTIONS;
        if ((!completeUnique && !completeWithFinalAlternatives)
                || solution.completedParts().size() != problem.parts().size()) {
            return new Verification(false, List.of("solver-did-not-complete-the-requested-parts"));
        }
        for (int i = 0; i < solution.completedParts().size(); i++) {
            ProjectileSequenceSolution.PartResult partResult = solution.completedParts().get(i);
            if (partResult.index() != i) issues.add("part-order-mismatch:" + i);
            if (partResult.solution() == null) {
                issues.add("missing-part-solution:" + i);
                continue;
            }
            verifyPart(problem.parts().get(i), partResult.solution(), i, issues);
        }
        return new Verification(issues.isEmpty(), issues);
    }

    private static void verifyPart(ProjectileProblemSequence.Part request, ProjectileSolution solution,
                                   int index, List<String> issues) {
        if (solution.target() != request.target()) issues.add("target-mismatch:" + index);
        if (solution.status() != ProjectileSolution.Status.SOLVED
                && solution.status() != ProjectileSolution.Status.MULTIPLE_SOLUTIONS) {
            issues.add("unsolved-status:" + index);
            return;
        }
        if (solution.candidates().isEmpty()) issues.add("no-candidates:" + index);
        if (solution.status() == ProjectileSolution.Status.SOLVED && solution.candidates().size() != 1) {
            issues.add("solved-candidate-count-mismatch:" + index);
        }
        if (solution.status() == ProjectileSolution.Status.MULTIPLE_SOLUTIONS && solution.candidates().size() < 2) {
            issues.add("multiple-status-candidate-count-mismatch:" + index);
        }
        if (solution.steps().isEmpty()) issues.add("no-derivation-steps:" + index);

        for (Map<String, PhysicalQuantity> candidate : solution.candidates()) {
            for (String required : solution.requiredQuantities()) {
                if (!candidate.containsKey(required)) issues.add("required-result-missing:" + index + ":" + required);
            }
            candidate.forEach((key, quantity) -> checkQuantity(quantity, "candidate:" + index + ":" + key, issues));
        }

        for (ProjectileSolution.Step step : solution.steps()) {
            if (step.equation() == null || step.interpretationId() == null || step.interpretationId().isBlank()) {
                issues.add("invalid-step-metadata:" + index);
            }
            step.substitutions().forEach((key, quantity) -> checkQuantity(quantity, "substitution:" + index + ":" + key, issues));
            step.intermediateResults().forEach((key, quantity) -> checkQuantity(quantity, "intermediate:" + index + ":" + key, issues));
            step.results().forEach((key, quantity) -> checkQuantity(quantity, "step-result:" + index + ":" + key, issues));
        }

        if (!solution.steps().isEmpty() && !solution.candidates().isEmpty()) {
            Map<String, PhysicalQuantity> candidate = solution.candidates().get(0);
            Map<String, PhysicalQuantity> results = solution.steps().get(solution.steps().size() - 1).results();
            for (Map.Entry<String, PhysicalQuantity> expected : candidate.entrySet()) {
                PhysicalQuantity actual = results.get(expected.getKey());
                if (actual == null) {
                    issues.add("candidate-not-associated-with-final-step:" + index + ":" + expected.getKey());
                } else if (!sameQuantity(expected.getValue(), actual)) {
                    issues.add("candidate-step-value-mismatch:" + index + ":" + expected.getKey());
                }
            }
        }
        verifyAnalyticalConsistency(solution, index, issues);
    }

    private static void verifyAnalyticalConsistency(ProjectileSolution solution, int index, List<String> issues) {
        try {
            Map<ProjectileProblem.Input, PhysicalQuantity> givens = solution.givens();
            double speed = value(givens, ProjectileProblem.Input.INITIAL_SPEED, 0.0);
            double angle = value(givens, ProjectileProblem.Input.LAUNCH_ANGLE, 0.0);
            double gravity = value(givens, ProjectileProblem.Input.GRAVITY, 0.0);
            double x0 = value(givens, ProjectileProblem.Input.INITIAL_X, 0.0);
            double y0 = value(givens, ProjectileProblem.Input.INITIAL_Y, 0.0);
            double landingY = value(givens, ProjectileProblem.Input.LANDING_Y, y0);
            ProjectileProblem.Target target = solution.target();
            if (target == ProjectileProblem.Target.FIND_LAUNCH_ANGLE) {
                double dx = value(givens, ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, 0.0);
                double dy = value(givens, ProjectileProblem.Input.VERTICAL_DISPLACEMENT, 0.0);
                for (Map<String, PhysicalQuantity> candidate : solution.candidates()) {
                    double candidateAngle = candidate.get("launchAngle").value();
                    ProjectileMotion model = new ProjectileMotion(speed, candidateAngle, gravity, 0.0, 0.0, 0.0);
                    ProjectileMotion.TimeSolutions times = model.timesAtHorizontalPosition(dx);
                    if (times.times().isEmpty() || times.times().stream()
                            .noneMatch(time -> close(model.stateAt(time).y(), dy))) {
                        issues.add("inverse-angle-residual:" + index);
                    }
                }
                return;
            }
            if (target == ProjectileProblem.Target.FIND_INITIAL_SPEED) {
                double candidateSpeed = solution.candidates().get(0).get("initialSpeed").value();
                ProjectileMotion model = new ProjectileMotion(candidateSpeed, angle, gravity, x0, y0, landingY);
                if (givens.containsKey(ProjectileProblem.Input.TARGET_VX)
                        && !close(model.initialVx(), value(givens, ProjectileProblem.Input.TARGET_VX, 0.0))) {
                    issues.add("inverse-speed-horizontal-velocity-residual:" + index);
                }
                if (givens.containsKey(ProjectileProblem.Input.TIME)
                        && givens.containsKey(ProjectileProblem.Input.TARGET_VY)
                        && !close(model.stateAt(value(givens, ProjectileProblem.Input.TIME, 0.0)).vy(),
                        value(givens, ProjectileProblem.Input.TARGET_VY, 0.0))) {
                    issues.add("inverse-speed-vertical-velocity-residual:" + index);
                }
                if (givens.containsKey(ProjectileProblem.Input.TIME)
                        && givens.containsKey(ProjectileProblem.Input.TARGET_SPEED)
                        && !close(model.stateAt(value(givens, ProjectileProblem.Input.TIME, 0.0)).speed(),
                        value(givens, ProjectileProblem.Input.TARGET_SPEED, 0.0))) {
                    issues.add("inverse-speed-speed-residual:" + index);
                }
                if (givens.containsKey(ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT)
                        && givens.containsKey(ProjectileProblem.Input.VERTICAL_DISPLACEMENT)) {
                    double dx = value(givens, ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, 0.0);
                    double dy = value(givens, ProjectileProblem.Input.VERTICAL_DISPLACEMENT, 0.0);
                    boolean reaches = model.timesAtHorizontalPosition(x0 + dx).times().stream()
                            .anyMatch(time -> close(model.stateAt(time).y(), y0 + dy));
                    if (!reaches) issues.add("inverse-speed-displacement-residual:" + index);
                }
                return;
            }

            ProjectileMotion model = new ProjectileMotion(speed, angle, gravity, x0, y0, landingY);
            switch (target) {
                case HORIZONTAL_RANGE -> compareOptional(solution, "range", model.horizontalRange(), index, issues);
                case MAXIMUM_HEIGHT -> compareOptional(solution, "maximumHeight", model.maximumHeight(), index, issues);
                case TIME_TO_APEX -> compareOptional(solution, "timeToApex", model.timeToApex(), index, issues);
                case FLIGHT_TIME -> compareOptional(solution, "flightTime", model.timeOfFlight(), index, issues);
                case SPEED_AT_APEX -> {
                    java.util.OptionalDouble apexSpeed = model.apexState().isPresent()
                            ? java.util.OptionalDouble.of(model.apexState().get().speed()) : java.util.OptionalDouble.empty();
                    compareOptional(solution, "speedAtApex", apexSpeed, index, issues);
                }
                case VELOCITY_AT_TIME, SPEED_AT_TIME, POSITION_AT_TIME, DISTANCE_TRAVELLED_AT_TIME -> {
                    double time = value(givens, ProjectileProblem.Input.TIME, 0.0);
                    ProjectileMotion.State state = model.stateAt(time);
                    for (Map<String, PhysicalQuantity> candidate : solution.candidates()) {
                        compareValue(candidate, "x", state.x(), index, issues);
                        compareValue(candidate, "y", state.y(), index, issues);
                        compareValue(candidate, "vx", state.vx(), index, issues);
                        compareValue(candidate, "vy", state.vy(), index, issues);
                        compareValue(candidate, "speed", state.speed(), index, issues);
                        compareValue(candidate, "distanceTravelled", model.distanceTravelledAt(time), index, issues);
                    }
                }
                case POSITION_AT_HORIZONTAL_DISPLACEMENT, VELOCITY_AT_HORIZONTAL_DISPLACEMENT,
                        SPEED_AT_HORIZONTAL_DISPLACEMENT, TIME_AT_HORIZONTAL_DISPLACEMENT -> {
                    double expectedX = x0 + value(givens, ProjectileProblem.Input.HORIZONTAL_DISPLACEMENT, 0.0);
                    for (Map<String, PhysicalQuantity> candidate : solution.candidates()) {
                        double time = candidate.get("time").value();
                        ProjectileMotion.State state = model.stateAt(time);
                        compareValue(candidate, "x", state.x(), index, issues);
                        compareValue(candidate, "y", state.y(), index, issues);
                        compareValue(candidate, "vx", state.vx(), index, issues);
                        compareValue(candidate, "vy", state.vy(), index, issues);
                        compareValue(candidate, "speed", state.speed(), index, issues);
                        if (!close(state.x(), expectedX)) issues.add("horizontal-displacement-residual:" + index);
                    }
                }
                case POSITION_AT_HEIGHT, VELOCITY_AT_HEIGHT, SPEED_AT_HEIGHT, TIMES_AT_HEIGHT -> {
                    double targetY = value(givens, ProjectileProblem.Input.TARGET_Y, 0.0);
                    for (Map<String, PhysicalQuantity> candidate : solution.candidates()) {
                        double time = candidate.get("time").value();
                        ProjectileMotion.State state = model.stateAt(time);
                        compareValue(candidate, "x", state.x(), index, issues);
                        compareValue(candidate, "y", state.y(), index, issues);
                        compareValue(candidate, "vx", state.vx(), index, issues);
                        compareValue(candidate, "vy", state.vy(), index, issues);
                        compareValue(candidate, "speed", state.speed(), index, issues);
                        if (!close(state.y(), targetY)) issues.add("height-query-residual:" + index);
                    }
                }
                case TIMES_AT_POSITION -> {
                    double targetX = value(givens, ProjectileProblem.Input.TARGET_X, 0.0);
                    double targetY = value(givens, ProjectileProblem.Input.TARGET_Y, 0.0);
                    for (Map<String, PhysicalQuantity> candidate : solution.candidates()) {
                        ProjectileMotion.State state = model.stateAt(candidate.get("time").value());
                        if (!close(state.x(), targetX) || !close(state.y(), targetY)) {
                            issues.add("position-query-residual:" + index);
                        }
                    }
                }
                case FIND_INITIAL_SPEED, FIND_LAUNCH_ANGLE -> throw new AssertionError("inverse target dispatch");
            }
        } catch (RuntimeException ex) {
            issues.add("analytical-crosscheck-failed:" + index);
        }
    }

    private static void compareOptional(ProjectileSolution solution, String key, java.util.OptionalDouble expected,
                                        int index, List<String> issues) {
        if (expected.isEmpty()) {
            issues.add("analytical-event-not-available:" + index + ":" + key);
            return;
        }
        for (Map<String, PhysicalQuantity> candidate : solution.candidates()) compareValue(candidate, key, expected.getAsDouble(), index, issues);
    }

    private static void compareValue(Map<String, PhysicalQuantity> candidate, String key, double expected,
                                     int index, List<String> issues) {
        PhysicalQuantity quantity = candidate.get(key);
        if (quantity != null && !close(quantity.valueInSi(), expected)) issues.add("analytical-result-mismatch:" + index + ":" + key);
    }

    private static double value(Map<ProjectileProblem.Input, PhysicalQuantity> values,
                                ProjectileProblem.Input input, double fallback) {
        PhysicalQuantity quantity = values.get(input);
        return quantity == null ? fallback : quantity.valueInSi();
    }

    private static boolean close(double left, double right) {
        if (!Double.isFinite(left) || !Double.isFinite(right)) return false;
        double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= RELATIVE_TOLERANCE * scale;
    }

    private static void checkQuantity(PhysicalQuantity quantity, String location, List<String> issues) {
        if (quantity == null || quantity.unit() == null || !Double.isFinite(quantity.value())) {
            issues.add("invalid-quantity:" + location);
        } else if (!Double.isFinite(quantity.valueInSi())) {
            issues.add("unit-conversion-overflow:" + location);
        }
    }

    private static boolean sameQuantity(PhysicalQuantity left, PhysicalQuantity right) {
        if (left == null || right == null || left.unit().dimension() != right.unit().dimension()) return false;
        double a = left.valueInSi();
        double b = right.valueInSi();
        double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= RELATIVE_TOLERANCE * scale;
    }

    public record Verification(boolean verified, List<String> issues) {
        public Verification {
            issues = List.copyOf(issues);
            if (verified && !issues.isEmpty()) throw new IllegalArgumentException("Verified output cannot contain issues");
        }
    }
}
